package run.halo.sync.publisher.adapter.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.halo.sync.publisher.adapter.PlatformAdapter;
import run.halo.sync.publisher.extension.SyncPlatform;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class WeChatPlatformAdapter implements PlatformAdapter {

    private static final String WECHAT_API_BASE = "https://api.weixin.qq.com/cgi-bin";
    private final WebClient webClient;

    public WeChatPlatformAdapter(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .baseUrl(WECHAT_API_BASE)
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();
    }

    @Override
    public SyncPlatform.PlatformType getPlatformType() {
        return SyncPlatform.PlatformType.WECHAT;
    }

    @Override
    public Mono<PublishResult> publish(String content, String title, SyncPlatform platform) {
        return getAccessToken(platform)
            .flatMap(accessToken -> uploadDraft(accessToken, content, title, platform))
            .timeout(Duration.ofSeconds(30))
            .onErrorResume(e -> {
                log.error("Failed to publish to WeChat: {}", e.getMessage());
                return Mono.error(new RuntimeException("微信公众号发布失败: " + e.getMessage()));
            });
    }

    @Override
    public Mono<PublishResult> update(String externalId, String content, String title, SyncPlatform platform) {
        // 微信公众号草稿更新逻辑
        return getAccessToken(platform)
            .flatMap(accessToken -> updateDraft(accessToken, externalId, content, title, platform))
            .timeout(Duration.ofSeconds(30))
            .onErrorResume(e -> {
                log.error("Failed to update WeChat draft: {}", e.getMessage());
                return Mono.error(new RuntimeException("微信公众号更新失败: " + e.getMessage()));
            });
    }

    @Override
    public Mono<Void> delete(String externalId, SyncPlatform platform) {
        // 微信公众号草稿删除逻辑
        return getAccessToken(platform)
            .flatMap(accessToken -> deleteDraft(accessToken, externalId))
            .timeout(Duration.ofSeconds(30))
            .then()
            .onErrorResume(e -> {
                log.error("Failed to delete WeChat draft: {}", e.getMessage());
                return Mono.error(new RuntimeException("微信公众号删除失败: " + e.getMessage()));
            });
    }

    @Override
    public Mono<Boolean> validateCredentials(SyncPlatform platform) {
        return getAccessToken(platform)
            .map(token -> true)
            .onErrorResume(e -> {
                log.warn("WeChat credentials validation failed: {}", e.getMessage());
                return Mono.just(false);
            });
    }

    /**
     * 获取 Access Token
     */
    private Mono<String> getAccessToken(SyncPlatform platform) {
        Map<String, String> credentials = platform.getSpec().getCredentials();
        if (credentials == null) {
            return Mono.error(new RuntimeException("未配置微信公众号凭证"));
        }

        String appId = credentials.get("appId");
        String appSecret = credentials.get("appSecret");

        if (appId == null || appSecret == null) {
            return Mono.error(new RuntimeException("微信公众号 AppId 或 AppSecret 未配置"));
        }

        return webClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/token")
                .queryParam("grant_type", "client_credential")
                .queryParam("appid", appId)
                .queryParam("secret", appSecret)
                .build())
            .retrieve()
            .bodyToMono(Map.class)
            .flatMap(response -> {
                if (response.containsKey("access_token")) {
                    return Mono.just((String) response.get("access_token"));
                }
                String errMsg = (String) response.getOrDefault("errmsg", "未知错误");
                return Mono.error(new RuntimeException("获取 Access Token 失败: " + errMsg));
            });
    }

    /**
     * 上传草稿
     */
    private Mono<PublishResult> uploadDraft(String accessToken, String content, String title, SyncPlatform platform) {
        String defaultThumbMediaId = getDefaultThumbMediaId(platform);

        // 构建草稿数据
        Map<String, Object> article = Map.of(
            "title", title,
            "content", convertToWechatFormat(content),
            "thumb_media_id", defaultThumbMediaId != null ? defaultThumbMediaId : "",
            "author", "",
            "digest", "",
            "content_source_url", "",
            "need_open_comment", 0,
            "only_fans_can_comment", 0
        );

        Map<String, Object> requestBody = Map.of("articles", new Object[]{article});

        return webClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/draft/add")
                .queryParam("access_token", accessToken)
                .build())
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map.class)
            .flatMap(response -> {
                if (response.containsKey("media_id")) {
                    String mediaId = (String) response.get("media_id");
                    return Mono.just(new PublishResult(mediaId, null, "草稿上传成功"));
                }
                String errMsg = (String) response.getOrDefault("errmsg", "未知错误");
                Integer errCode = (Integer) response.get("errcode");
                return Mono.error(new RuntimeException("上传草稿失败 [" + errCode + "]: " + errMsg));
            });
    }

    /**
     * 更新草稿
     */
    private Mono<PublishResult> updateDraft(String accessToken, String mediaId, String content, String title, SyncPlatform platform) {
        String defaultThumbMediaId = getDefaultThumbMediaId(platform);

        Map<String, Object> article = Map.of(
            "title", title,
            "content", convertToWechatFormat(content),
            "thumb_media_id", defaultThumbMediaId != null ? defaultThumbMediaId : "",
            "author", "",
            "digest", "",
            "content_source_url", ""
        );

        Map<String, Object> requestBody = Map.of(
            "media_id", mediaId,
            "index", 0,
            "articles", article
        );

        return webClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/draft/update")
                .queryParam("access_token", accessToken)
                .build())
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map.class)
            .flatMap(response -> {
                Integer errcode = (Integer) response.get("errcode");
                if (errcode != null && errcode == 0) {
                    return Mono.just(new PublishResult(mediaId, null, "草稿更新成功"));
                }
                String errMsg = (String) response.getOrDefault("errmsg", "未知错误");
                return Mono.error(new RuntimeException("更新草稿失败 [" + errcode + "]: " + errMsg));
            });
    }

    /**
     * 删除草稿
     */
    private Mono<Void> deleteDraft(String accessToken, String mediaId) {
        return webClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/draft/delete")
                .queryParam("access_token", accessToken)
                .build())
            .bodyValue(Map.of("media_id", mediaId))
            .retrieve()
            .bodyToMono(Map.class)
            .flatMap(response -> {
                Integer errcode = (Integer) response.get("errcode");
                if (errcode != null && errcode == 0) {
                    return Mono.empty();
                }
                String errMsg = (String) response.getOrDefault("errmsg", "未知错误");
                return Mono.error(new RuntimeException("删除草稿失败 [" + errcode + "]: " + errMsg));
            });
    }

    /**
     * 转换为微信格式（简化处理）
     */
    private String convertToWechatFormat(String markdown) {
        // TODO: 完整的 Markdown -> HTML 转换
        // 这里简化处理，实际应该使用 Markdown 解析器
        return markdown
            .replace("\n", "<br/>")
            .replace("# ", "<h1>")
            .replace("## ", "<h2>")
            .replace("### ", "<h3>");
    }

    /**
     * 获取默认封面图 media_id
     */
    private String getDefaultThumbMediaId(SyncPlatform platform) {
        Map<String, String> credentials = platform.getSpec().getCredentials();
        return credentials != null ? credentials.get("defaultThumbMediaId") : null;
    }
}
