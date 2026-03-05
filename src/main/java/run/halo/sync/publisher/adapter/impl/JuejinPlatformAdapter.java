package run.halo.sync.publisher.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.halo.sync.publisher.adapter.PlatformAdapter;
import run.halo.sync.publisher.adapter.dto.ArticleContent;
import run.halo.sync.publisher.adapter.dto.PublishResult;
import run.halo.sync.publisher.extension.SyncPlatform;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 掘金平台适配器
 * 
 * 掘金开放 API 文档：https://juejin.cn/developer/api
 * 
 * 注意：掘金 API 需要通过 Cookie 或 Token 认证
 */
@Slf4j
@Component
public class JuejinPlatformAdapter implements PlatformAdapter {

    private static final String JUEJIN_API_BASE = "https://api.juejin.cn";
    private static final String JUEJIN_WEB_BASE = "https://juejin.cn";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public JuejinPlatformAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
            .baseUrl(JUEJIN_API_BASE)
            .codecs(configurer -> configurer
                .defaultCodecs()
                .maxInMemorySize(10 * 1024 * 1024))
            .build();
    }

    @Override
    public SyncPlatform.PlatformType getPlatformType() {
        return SyncPlatform.PlatformType.JUEJIN;
    }

    @Override
    public Mono<PublishResult> publish(ArticleContent content, SyncPlatform platform) {
        return createDraft(content, platform)
            .flatMap(articleId -> publishDraft(articleId, platform))
            .doOnSuccess(result -> {
                if (result.getSuccess()) {
                    log.info("Successfully published article to Juejin: {}", content.getTitle());
                } else {
                    log.error("Failed to publish article to Juejin: {}", result.getErrorMessage());
                }
            })
            .onErrorResume(e -> {
                log.error("Error publishing to Juejin", e);
                return Mono.just(PublishResult.failure("JUEJIN_ERROR", e.getMessage()));
            });
    }

    @Override
    public Mono<PublishResult> update(String externalId, ArticleContent content, SyncPlatform platform) {
        // 掘金更新文章需要先删除再发布
        return delete(externalId, platform)
            .then(publish(content, platform));
    }

    @Override
    public Mono<Void> delete(String externalId, SyncPlatform platform) {
        Map<String, String> credentials = platform.getSpec().getCredentials();
        if (credentials == null || !credentials.containsKey("cookie")) {
            return Mono.error(new RuntimeException("Juejin cookie not configured"));
        }

        String cookie = credentials.get("cookie");

        Map<String, Object> body = new HashMap<>();
        body.put("article_id", externalId);

        return webClient.post()
            .uri("/content_api/v1/article/delete")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String.class)
            .then()
            .onErrorResume(e -> {
                log.error("Error deleting from Juejin", e);
                return Mono.empty();
            });
    }

    @Override
    public Mono<Boolean> testConnection(SyncPlatform platform) {
        Map<String, String> credentials = platform.getSpec().getCredentials();
        if (credentials == null || !credentials.containsKey("cookie")) {
            return Mono.just(false);
        }

        return webClient.post()
            .uri("/user_api/v1/user/get")
            .header("Cookie", credentials.get("cookie"))
            .retrieve()
            .bodyToMono(String.class)
            .map(response -> {
                try {
                    JsonNode json = objectMapper.readTree(response);
                    return json.has("data") && json.get("data").has("user_id");
                } catch (Exception e) {
                    return false;
                }
            })
            .onErrorResume(e -> Mono.just(false));
    }

    // ==================== Private Methods ====================

    /**
     * 创建草稿
     */
    private Mono<String> createDraft(ArticleContent content, SyncPlatform platform) {
        Map<String, String> credentials = platform.getSpec().getCredentials();
        if (credentials == null || !credentials.containsKey("cookie")) {
            return Mono.error(new RuntimeException("Juejin cookie not configured"));
        }

        String cookie = credentials.get("cookie");

        // 构建文章数据
        Map<String, Object> article = new HashMap<>();
        article.put("title", content.getTitle());
        article.put("mark_content", content.getRawContent());
        article.put("content", content.getHtmlContent() != null ? 
            content.getHtmlContent() : markdownToHtml(content.getRawContent()));
        article.put("cover_image", content.getCoverImage() != null ? 
            content.getCoverImage() : "");
        article.put("category_id", "0"); // 默认分类
        article.put("tag_ids", List.of());
        article.put("theme_ids", List.of());
        article.put("brief_content", content.getSummary() != null ? 
            content.getSummary() : generateBrief(content));
        
        // 禁止转载设置
        article.put("edit_type", 10); // 10 = Markdown
        
        Map<String, Object> body = new HashMap<>();
        body.put("article", article);

        return webClient.post()
            .uri("/content_api/v1/article_draft/create")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String.class)
            .flatMap(response -> {
                try {
                    JsonNode json = objectMapper.readTree(response);
                    
                    if (!"success".equals(json.path("message").asText())) {
                        return Mono.error(new RuntimeException(
                            "Juejin API error: " + json.path("message").asText()));
                    }
                    
                    String articleId = json.path("data").path("article_id").asText();
                    log.info("✅ Draft created on Juejin: {}", articleId);
                    
                    return Mono.just(articleId);
                } catch (Exception e) {
                    return Mono.error(new RuntimeException(
                        "Failed to parse Juejin response", e));
                }
            });
    }

    /**
     * 发布草稿
     */
    private Mono<PublishResult> publishDraft(String articleId, SyncPlatform platform) {
        Map<String, String> credentials = platform.getSpec().getCredentials();
        String cookie = credentials.get("cookie");

        Map<String, Object> body = new HashMap<>();
        body.put("article_id", articleId);
        body.put("sync_to_org", false);

        return webClient.post()
            .uri("/content_api/v1/article/publish")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String.class)
            .map(response -> {
                try {
                    JsonNode json = objectMapper.readTree(response);
                    
                    if (!"success".equals(json.path("message").asText())) {
                        return PublishResult.failure(
                            "JUEJIN_PUBLISH_ERROR",
                            json.path("message").asText()
                        );
                    }
                    
                    String publishedArticleId = json.path("data").path("article_id").asText();
                    String articleUrl = JUEJIN_WEB_BASE + "/post/" + publishedArticleId;
                    
                    log.info("✅ Article published on Juejin: {}", articleUrl);
                    
                    return PublishResult.success(publishedArticleId, articleUrl);
                } catch (Exception e) {
                    return PublishResult.failure("PARSE_ERROR", e.getMessage());
                }
            });
    }

    /**
     * 生成摘要（掘金需要）
     */
    private String generateBrief(ArticleContent content) {
        String text = content.getRawContent();
        if (text == null) {
            return "";
        }
        
        // 移除 Markdown 标记
        text = text.replaceAll("#+\\s*", "")
                   .replaceAll("\\*+", "")
                   .replaceAll("`+", "")
                   .replaceAll("\\[.+?\\]\\(.+?\\)", "")
                   .replaceAll("\n+", " ")
                   .trim();
        
        // 截取前 100 字符
        return text.length() > 100 ? text.substring(0, 100) + "..." : text;
    }

    /**
     * Markdown 转 HTML（简化版）
     */
    private String markdownToHtml(String markdown) {
        if (markdown == null) {
            return "";
        }
        
        return markdown
            .replaceAll("(?m)^### (.+)$", "<h3>$1</h3>")
            .replaceAll("(?m)^## (.+)$", "<h2>$1</h2>")
            .replaceAll("(?m)^# (.+)$", "<h1>$1</h1>")
            .replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>")
            .replaceAll("\\*(.+?)\\*", "<em>$1</em>")
            .replaceAll("\n\n", "</p><p>")
            .replaceAll("\n", "<br/>");
    }
}
