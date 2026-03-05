package run.halo.sync.publisher.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.sync.publisher.extension.SyncPlatform;
import run.halo.sync.publisher.service.ImageUploader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * 图片上传服务实现
 * 
 * 支持功能：
 * - 从 URL 下载图片并上传到微信
 * - 上传本地图片文件到微信
 * - 图片格式自动检测
 */
@Slf4j
@Service
public class ImageUploaderImpl implements ImageUploader {

    private static final String WECHAT_API_BASE = "https://api.weixin.qq.com/cgi-bin";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final WeChatTokenManager tokenManager;

    public ImageUploaderImpl(ObjectMapper objectMapper, WeChatTokenManager tokenManager) {
        this.objectMapper = objectMapper;
        this.tokenManager = tokenManager;
        this.webClient = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(
                reactor.netty.http.client.HttpClient.create()
                    .responseTimeout(TIMEOUT)
            ))
            .codecs(configurer -> configurer
                .defaultCodecs()
                .maxInMemorySize((int) MAX_FILE_SIZE))
            .build();
    }

    @Override
    public Mono<String> uploadToWeChat(String imageUrl, SyncPlatform platform) {
        return uploadImage(imageUrl, platform)
            .map(UploadResult::mediaId);
    }

    @Override
    public Mono<UploadResult> uploadImage(String imageSource, SyncPlatform platform) {
        // 判断图片来源
        if (imageSource.startsWith("http://") || imageSource.startsWith("https://")) {
            return downloadAndUpload(imageSource, platform);
        } else {
            return uploadLocalFile(imageSource, platform);
        }
    }

    // ==================== 从 URL 下载并上传 ====================

    /**
     * 从 URL 下载图片并上传到微信
     */
    private Mono<UploadResult> downloadAndUpload(String imageUrl, SyncPlatform platform) {
        log.info("Downloading image from: {}", imageUrl);

        return webClient.get()
            .uri(imageUrl)
            .retrieve()
            .bodyToFlux(DataBuffer.class)
            .collectList()
            .flatMap(buffers -> {
                // 合并数据
                DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
                DataBuffer combined = factory.join(buffers);
                byte[] bytes = new byte[combined.readableByteCount()];
                combined.read(bytes);
                DataBufferUtils.release(combined);

                // 检测 MIME 类型
                String mimeType = detectMimeType(bytes, imageUrl);
                String filename = extractFilename(imageUrl, mimeType);

                log.info("Downloaded {} bytes, type: {}", bytes.length, mimeType);

                return uploadToWeChatInternal(bytes, filename, mimeType, platform);
            })
            .onErrorResume(e -> {
                log.error("Failed to download image: {}", imageUrl, e);
                return Mono.empty();
            });
    }

    // ==================== 上传本地文件 ====================

    /**
     * 上传本地图片文件
     */
    private Mono<UploadResult> uploadLocalFile(String filePath, SyncPlatform platform) {
        return Mono.fromCallable(() -> {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                throw new RuntimeException("File not found: " + filePath);
            }
            return path;
        })
        .flatMap(path -> {
            try {
                byte[] bytes = Files.readAllBytes(path);
                String mimeType = detectMimeType(bytes, filePath);
                String filename = path.getFileName().toString();

                log.info("Read local file {} bytes, type: {}", bytes.length, mimeType);

                return uploadToWeChatInternal(bytes, filename, mimeType, platform);
            } catch (Exception e) {
                return Mono.error(e);
            }
        });
    }

    // ==================== 微信上传核心逻辑 ====================

    /**
     * 上传图片到微信（图文消息内图片）
     * 用于文章内容中的图片
     */
    private Mono<UploadResult> uploadToWeChatInternal(
            byte[] imageData, 
            String filename, 
            String mimeType,
            SyncPlatform platform) {
        
        return tokenManager.getAccessToken(platform)
            .flatMap(accessToken -> {
                String url = WECHAT_API_BASE + 
                    "/media/uploadimg?access_token=" + accessToken;

                // 构建 multipart/form-data
                MultipartBodyBuilder builder = new MultipartBodyBuilder();
                builder.part("media", 
                    new org.springframework.core.io.ByteArrayResource(imageData) {
                        @Override
                        public String getFilename() {
                            return filename;
                        }
                    },
                    MediaType.parseMediaType(mimeType)
                );

                return webClient.post()
                    .uri(url)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .flatMap(response -> parseWeChatResponse(response));
            });
    }

    /**
     * 上传永久素材（用于封面图）
     */
    public Mono<UploadResult> uploadPermanentMaterial(
            byte[] imageData,
            String filename,
            String mimeType,
            SyncPlatform platform) {
        
        return tokenManager.getAccessToken(platform)
            .flatMap(accessToken -> {
                String url = WECHAT_API_BASE + 
                    "/material/add_material?access_token=" + accessToken + "&type=thumb";

                MultipartBodyBuilder builder = new MultipartBodyBuilder();
                builder.part("media", 
                    new org.springframework.core.io.ByteArrayResource(imageData) {
                        @Override
                        public String getFilename() {
                            return filename;
                        }
                    },
                    MediaType.parseMediaType(mimeType)
                );

                return webClient.post()
                    .uri(url)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .flatMap(response -> parseWeChatMaterialResponse(response));
            });
    }

    // ==================== 响应解析 ====================

    private Mono<UploadResult> parseWeChatResponse(String response) {
        try {
            JsonNode json = objectMapper.readTree(response);

            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                return Mono.error(new RuntimeException(
                    "WeChat upload error: " + json.get("errmsg").asText()));
            }

            String url = json.has("url") ? json.get("url").asText() : null;
            log.info("✅ Image uploaded to WeChat: {}", url);

            return Mono.just(new UploadResult(url, null, null));
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Failed to parse response", e));
        }
    }

    private Mono<UploadResult> parseWeChatMaterialResponse(String response) {
        try {
            JsonNode json = objectMapper.readTree(response);

            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                return Mono.error(new RuntimeException(
                    "WeChat upload error: " + json.get("errmsg").asText()));
            }

            String mediaId = json.has("media_id") ? json.get("media_id").asText() : null;
            String url = json.has("url") ? json.get("url").asText() : null;
            
            log.info("✅ Permanent material uploaded: media_id={}, url={}", mediaId, url);

            return Mono.just(new UploadResult(url, mediaId, null));
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Failed to parse response", e));
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 检测 MIME 类型
     */
    private String detectMimeType(byte[] bytes, String filename) {
        // 简单的魔数检测
        if (bytes.length >= 8) {
            // PNG: 89 50 4E 47
            if (bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && 
                bytes[2] == 0x4E && bytes[3] == 0x47) {
                return "image/png";
            }
            // JPEG: FF D8 FF
            if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && 
                bytes[2] == (byte) 0xFF) {
                return "image/jpeg";
            }
            // GIF: 47 49 46 38
            if (bytes[0] == 0x47 && bytes[1] == 0x49 && 
                bytes[2] == 0x46 && bytes[3] == 0x38) {
                return "image/gif";
            }
            // WebP: 52 49 46 46 ... 57 45 42 50
            if (bytes[0] == 0x52 && bytes[1] == 0x49 && 
                bytes[2] == 0x46 && bytes[3] == 0x46) {
                return "image/webp";
            }
        }

        // 从扩展名检测
        String ext = filename.toLowerCase();
        if (ext.endsWith(".png")) return "image/png";
        if (ext.endsWith(".jpg") || ext.endsWith(".jpeg")) return "image/jpeg";
        if (ext.endsWith(".gif")) return "image/gif";
        if (ext.endsWith(".webp")) return "image/webp";

        // 默认
        return "image/jpeg";
    }

    /**
     * 提取文件名
     */
    private String extractFilename(String url, String mimeType) {
        String ext = ".jpg";
        if (mimeType.contains("png")) ext = ".png";
        else if (mimeType.contains("gif")) ext = ".gif";
        else if (mimeType.contains("webp")) ext = ".webp";

        // 从 URL 提取
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0) {
            String name = url.substring(lastSlash + 1);
            int queryIndex = name.indexOf('?');
            if (queryIndex > 0) {
                name = name.substring(0, queryIndex);
            }
            if (name.contains(".")) {
                return name;
            }
        }

        return "image_" + UUID.randomUUID().toString().substring(0, 8) + ext;
    }
}
