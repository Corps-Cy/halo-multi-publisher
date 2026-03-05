package run.halo.sync.publisher.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import run.halo.sync.publisher.adapter.PlatformAdapter;
import run.halo.sync.publisher.adapter.dto.ArticleContent;
import run.halo.sync.publisher.adapter.dto.PublishResult;
import run.halo.sync.publisher.extension.SyncPlatform;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 微信公众号平台适配器
 * 
 * 基于已有的 wechat-api.ts 实现，核心功能：
 * - 获取 Access Token（带缓存）
 * - 上传图片素材
 * - 创建草稿
 * - 发布草稿
 * 
 * 官方 API 文档：https://developers.weixin.qq.com/doc/offiaccount/Asset_Management/Adding_Permanent_Assets.html
 */
@Slf4j
@Component
public class WeChatPlatformAdapter implements PlatformAdapter {

    private static final String WECHAT_API_BASE = "https://api.weixin.qq.com/cgi-bin";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration TOKEN_EXPIRE_MARGIN = Duration.ofMinutes(5);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    // Access Token 缓存
    private final AtomicReference<TokenCache> tokenCache = new AtomicReference<>();

    public WeChatPlatformAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
            .baseUrl(WECHAT_API_BASE)
            .clientConnector(new ReactorClientHttpConnector(
                HttpClient.create().responseTimeout(TIMEOUT)
            ))
            .codecs(configurer -> configurer
                .defaultCodecs()
                .maxInMemorySize(10 * 1024 * 1024))
            .build();
    }

    @Override
    public SyncPlatform.PlatformType getPlatformType() {
        return SyncPlatform.PlatformType.WECHAT;
    }

    @Override
    public Mono<PublishResult> publish(ArticleContent content, SyncPlatform platform) {
        return getAccessToken(platform)
            .flatMap(accessToken -> createDraft(content, accessToken, platform))
            .doOnSuccess(result -> {
                if (result.getSuccess()) {
                    log.info("Successfully published article to WeChat: {}", content.getTitle());
                } else {
                    log.error("Failed to publish article to WeChat: {}", result.getErrorMessage());
                }
            })
            .onErrorResume(e -> {
                log.error("Error publishing to WeChat", e);
                return Mono.just(PublishResult.failure("WECHAT_ERROR", e.getMessage()));
            });
    }

    @Override
    public Mono<PublishResult> update(String externalId, ArticleContent content, SyncPlatform platform) {
        return getAccessToken(platform)
            .flatMap(accessToken -> updateDraft(externalId, content, accessToken, platform))
            .doOnSuccess(result -> {
                if (result.getSuccess()) {
                    log.info("Successfully updated article on WeChat: {}", externalId);
                } else {
                    log.error("Failed to update article on WeChat: {}", result.getErrorMessage());
                }
            })
            .onErrorResume(e -> {
                log.error("Error updating on WeChat", e);
                return Mono.just(PublishResult.failure("WECHAT_ERROR", e.getMessage()));
            });
    }

    @Override
    public Mono<Void> delete(String externalId, SyncPlatform platform) {
        return getAccessToken(platform)
            .flatMap(accessToken -> deleteMaterial(externalId, accessToken))
            .then()
            .onErrorResume(e -> {
                log.error("Error deleting from WeChat", e);
                return Mono.empty();
            });
    }

    @Override
    public Mono<Boolean> testConnection(SyncPlatform platform) {
        return getAccessToken(platform)
            .map(token -> true)
            .onErrorResume(e -> {
                log.warn("WeChat connection test failed", e);
                return Mono.just(false);
            });
    }

    // ==================== Token Management ====================

    /**
     * 获取 Access Token（带缓存）
     * 参考原 wechat-api.ts: getAccessToken
     */
    private Mono<String> getAccessToken(SyncPlatform platform) {
        // 检查缓存
        TokenCache cached = tokenCache.get();
        String cacheKey = getCacheKey(platform);
        
        if (cached != null && cached.isValid(cacheKey)) {
            log.debug("Using cached access token");
            return Mono.just(cached.token);
        }

        Map<String, String> credentials = platform.getSpec().getCredentials();
        if (credentials == null) {
            return Mono.error(new RuntimeException("WeChat credentials not configured"));
        }

        String appId = credentials.get("appId");
        String appSecret = credentials.get("appSecret");

        if (appId == null || appSecret == null) {
            return Mono.error(new RuntimeException("WeChat appId or appSecret not configured"));
        }

        return webClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/token")
                .queryParam("grant_type", "client_credential")
                .queryParam("appid", appId)
                .queryParam("secret", appSecret)
                .build())
            .retrieve()
            .bodyToMono(String.class)
            .flatMap(this::parseTokenResponse)
            .map(token -> {
                // 更新缓存
                tokenCache.set(new TokenCache(cacheKey, token, 
                    System.currentTimeMillis() + 7200 * 1000 - TOKEN_EXPIRE_MARGIN.toMillis()));
                log.info("✅ Access token obtained and cached");
                return token;
            });
    }

    private Mono<String> parseTokenResponse(String response) {
        try {
            JsonNode json = objectMapper.readTree(response);
            
            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                return Mono.error(new RuntimeException(
                    "WeChat API error: " + json.get("errmsg").asText() + 
                    " (" + json.get("errcode").asInt() + ")"
                ));
            }
            
            return Mono.just(json.get("access_token").asText());
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Failed to parse token response", e));
        }
    }

    private String getCacheKey(SyncPlatform platform) {
        Map<String, String> credentials = platform.getSpec().getCredentials();
        return credentials != null ? credentials.getOrDefault("appId", "") : "";
    }

    // ==================== Draft Operations ====================

    /**
     * 创建草稿
     * 参考原 wechat-api.ts: createDraft
     */
    private Mono<PublishResult> createDraft(ArticleContent content, String accessToken, SyncPlatform platform) {
        // 构建文章数据
        Map<String, Object> article = new HashMap<>();
        article.put("title", content.getTitle());
        article.put("author", content.getAuthorName() != null ? content.getAuthorName() : "");
        article.put("content", convertToWeChatHtml(content));
        article.put("digest", content.getSummary() != null ? content.getSummary() : "");
        article.put("content_source_url", content.getSourceUrl() != null ? content.getSourceUrl() : "");
        article.put("need_open_comment", 0);
        article.put("only_fans_can_comment", 0);
        
        // 封面图处理（需要先上传获取 thumb_media_id）
        String thumbMediaId = getThumbMediaId(content, platform);
        if (thumbMediaId != null && !thumbMediaId.isEmpty()) {
            article.put("thumb_media_id", thumbMediaId);
        } else {
            log.warn("⚠️ No cover image provided for WeChat draft");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("articles", new Object[]{article});

        return webClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/draft/add")
                .queryParam("access_token", accessToken)
                .build())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String.class)
            .flatMap(this::parseDraftResponse);
    }

    /**
     * 更新草稿
     */
    private Mono<PublishResult> updateDraft(String mediaId, ArticleContent content, 
            String accessToken, SyncPlatform platform) {
        Map<String, Object> article = new HashMap<>();
        article.put("title", content.getTitle());
        article.put("author", content.getAuthorName() != null ? content.getAuthorName() : "");
        article.put("content", convertToWeChatHtml(content));
        article.put("digest", content.getSummary() != null ? content.getSummary() : "");
        article.put("content_source_url", content.getSourceUrl() != null ? content.getSourceUrl() : "");

        Map<String, Object> body = new HashMap<>();
        body.put("media_id", mediaId);
        body.put("index", 0);
        body.put("articles", new Object[]{article});

        return webClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/draft/update")
                .queryParam("access_token", accessToken)
                .build())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String.class)
            .flatMap(response -> parseBaseResponse(response, mediaId));
    }

    /**
     * 删除素材
     */
    private Mono<Void> deleteMaterial(String mediaId, String accessToken) {
        Map<String, String> body = new HashMap<>();
        body.put("media_id", mediaId);

        return webClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/material/del_material")
                .queryParam("access_token", accessToken)
                .build())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String.class)
            .then();
    }

    // ==================== Response Parsing ====================

    private Mono<PublishResult> parseDraftResponse(String response) {
        try {
            JsonNode json = objectMapper.readTree(response);
            
            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                int errcode = json.get("errcode").asInt();
                String errmsg = json.get("errmsg").asText();
                
                // 特殊错误处理
                if (errcode == 40007) {
                    return Mono.just(PublishResult.failure(
                        "INVALID_MEDIA_ID",
                        "需要上传封面图片。请在公众号后台上传封面，或配置插件自动上传封面功能。"
                    ));
                }
                
                return Mono.just(PublishResult.failure(
                    String.valueOf(errcode), 
                    errmsg
                ));
            }
            
            String mediaId = json.get("media_id").asText();
            log.info("✅ Draft created successfully: {}", mediaId);
            
            return Mono.just(PublishResult.success(mediaId, null));
        } catch (Exception e) {
            return Mono.just(PublishResult.failure("PARSE_ERROR", 
                "Failed to parse response: " + e.getMessage()));
        }
    }

    private Mono<PublishResult> parseBaseResponse(String response, String mediaId) {
        try {
            JsonNode json = objectMapper.readTree(response);
            
            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                return Mono.just(PublishResult.failure(
                    String.valueOf(json.get("errcode").asInt()),
                    json.get("errmsg").asText()
                ));
            }
            
            return Mono.just(PublishResult.success(mediaId, null));
        } catch (Exception e) {
            return Mono.just(PublishResult.failure("PARSE_ERROR", e.getMessage()));
        }
    }

    // ==================== Content Conversion ====================

    /**
     * 获取封面图 thumb_media_id
     * TODO: 实现图片上传逻辑
     */
    private String getThumbMediaId(ArticleContent content, SyncPlatform platform) {
        // 优先使用配置的默认封面
        Map<String, String> credentials = platform.getSpec().getCredentials();
        if (credentials != null && credentials.containsKey("defaultThumbMediaId")) {
            return credentials.get("defaultThumbMediaId");
        }
        
        // TODO: 如果文章有封面图，需要上传到微信获取 media_id
        // 参考 wechat-api.ts: uploadPermanentMaterial
        
        return null;
    }

    /**
     * 将内容转换为微信公众号支持的 HTML 格式
     * 
     * 参考原 wechat-formatter-fixed.ts 的实现：
     * - 代码高亮
     * - Mac 风格代码块
     * - 列表处理（微信不支持 list-style）
     * - 表格、图片等
     */
    private String convertToWeChatHtml(ArticleContent content) {
        String html = content.getHtmlContent();
        
        if (html == null || html.isEmpty()) {
            html = markdownToWeChatHtml(content.getRawContent());
        }
        
        // 应用微信专用样式
        html = applyWeChatStyles(html);
        
        return html;
    }

    /**
     * Markdown 转微信 HTML（简化版）
     * 
     * TODO: 实现完整的格式化器，参考 wechat-formatter-fixed.ts
     * - 代码高亮（github-dark-dimmed）
     * - Mac 风格代码块
     * - 行号
     * - 列表前缀
     */
    private String markdownToWeChatHtml(String markdown) {
        if (markdown == null) {
            return "";
        }
        
        // 简单的 Markdown 转换
        String html = markdown
            // 标题
            .replaceAll("(?m)^### (.+)$", "<h3>$1</h3>")
            .replaceAll("(?m)^## (.+)$", "<h2>$1</h2>")
            .replaceAll("(?m)^# (.+)$", "<h1>$1</h1>")
            // 粗体
            .replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>")
            // 斜体
            .replaceAll("\\*(.+?)\\*", "<em>$1</em>")
            // 代码块
            .replaceAll("```(\\w*)\\n([\\s\\S]*?)```", 
                "<pre><code class=\"language-$1\">$2</code></pre>")
            // 行内代码
            .replaceAll("`(.+?)`", "<code>$1</code>")
            // 链接
            .replaceAll("\\[(.+?)\\]\\((.+?)\\)", "<a href=\"$2\">$1</a>")
            // 换行
            .replaceAll("\n\n", "</p><p>")
            .replaceAll("\n", "<br/>");
        
        return "<section><p>" + html + "</p></section>";
    }

    /**
     * 应用微信专用样式
     */
    private String applyWeChatStyles(String html) {
        // 基础样式
        String sectionStyle = "padding: 20px 15px; font-size: 14px; line-height: 1.75; " +
            "color: #333; font-family: -apple-system-font, BlinkMacSystemFont, " +
            "'Helvetica Neue', 'PingFang SC', 'Hiragino Sans GB', " +
            "'Microsoft YaHei UI', 'Microsoft YaHei', Arial, sans-serif; " +
            "text-align: left;";
        
        // 代码块样式（Mac 风格）
        String codeBlockStyle = "margin: 10px 8px; border-radius: 5px; " +
            "overflow: hidden; background: #22272e; padding: 16px;";
        
        String codeStyle = "color: #adbac7; font-size: 12.6px; line-height: 1.5; " +
            "font-family: Menlo, Monaco, 'Courier New', monospace;";
        
        // 引用样式
        String blockquoteStyle = "font-style: normal; padding: 1em; " +
            "border-left: 4px solid #0F4C81; border-radius: 6px; " +
            "color: #333; background: #f0f5fa; margin: 0 8px 1em;";
        
        // 应用样式
        html = html.replace("<section>", "<section style=\"" + sectionStyle + "\">");
        html = html.replace("<pre>", "<pre style=\"" + codeBlockStyle + "\">");
        html = html.replace("<code>", "<code style=\"" + codeStyle + "\">");
        html = html.replace("<blockquote>", "<blockquote style=\"" + blockquoteStyle + "\">");
        
        return html;
    }

    // ==================== Inner Classes ====================

    /**
     * Token 缓存
     */
    private static class TokenCache {
        final String cacheKey;
        final String token;
        final long expiresAt;

        TokenCache(String cacheKey, String token, long expiresAt) {
            this.cacheKey = cacheKey;
            this.token = token;
            this.expiresAt = expiresAt;
        }

        boolean isValid(String key) {
            return cacheKey.equals(key) && System.currentTimeMillis() < expiresAt;
        }
    }
}
