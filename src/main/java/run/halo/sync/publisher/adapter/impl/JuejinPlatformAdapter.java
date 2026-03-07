package run.halo.sync.publisher.adapter.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.halo.sync.publisher.adapter.PlatformAdapter;
import run.halo.sync.publisher.extension.SyncPlatform;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class JuejinPlatformAdapter implements PlatformAdapter {

    private static final String JUEJIN_API_BASE = "https://api.juejin.cn/content_api/v1";
    private final WebClient webClient;

    public JuejinPlatformAdapter(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .baseUrl(JUEJIN_API_BASE)
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();
    }

    @Override
    public SyncPlatform.PlatformType getPlatformType() {
        return SyncPlatform.PlatformType.JUEJIN;
    }

    @Override
    public Mono<PublishResult> publish(String content, String title, SyncPlatform platform) {
        String cookie = getCookie(platform);
        if (cookie == null || cookie.isEmpty()) {
            return Mono.error(new RuntimeException("未配置掘金 Cookie"));
        }

        return createDraft(cookie, content, title)
            .timeout(Duration.ofSeconds(30))
            .onErrorResume(e -> {
                log.error("Failed to publish to Juejin: {}", e.getMessage());
                return Mono.error(new RuntimeException("掘金发布失败: " + e.getMessage()));
            });
    }

    @Override
    public Mono<PublishResult> update(String externalId, String content, String title, SyncPlatform platform) {
        String cookie = getCookie(platform);
        if (cookie == null || cookie.isEmpty()) {
            return Mono.error(new RuntimeException("未配置掘金 Cookie"));
        }

        return updateDraft(cookie, externalId, content, title)
            .timeout(Duration.ofSeconds(30))
            .onErrorResume(e -> {
                log.error("Failed to update Juejin article: {}", e.getMessage());
                return Mono.error(new RuntimeException("掘金更新失败: " + e.getMessage()));
            });
    }

    @Override
    public Mono<Void> delete(String externalId, SyncPlatform platform) {
        String cookie = getCookie(platform);
        if (cookie == null || cookie.isEmpty()) {
            return Mono.error(new RuntimeException("未配置掘金 Cookie"));
        }

        return deleteDraft(cookie, externalId)
            .timeout(Duration.ofSeconds(30))
            .onErrorResume(e -> {
                log.error("Failed to delete Juejin article: {}", e.getMessage());
                return Mono.error(new RuntimeException("掘金删除失败: " + e.getMessage()));
            });
    }

    @Override
    public Mono<Boolean> validateCredentials(SyncPlatform platform) {
        String cookie = getCookie(platform);
        if (cookie == null || cookie.isEmpty()) {
            return Mono.just(false);
        }

        return webClient.post()
            .uri("/user_api/v1/user/get")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of())
            .retrieve()
            .bodyToMono(Map.class)
            .map(response -> {
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                return data != null && data.containsKey("user_name");
            })
            .onErrorResume(e -> {
                log.warn("Juejin credentials validation failed: {}", e.getMessage());
                return Mono.just(false);
            });
    }

    /**
     * 创建草稿
     */
    private Mono<PublishResult> createDraft(String cookie, String content, String title) {
        Map<String, Object> requestBody = Map.of(
            "category_id", "0", // 0 表示未分类
            "tag_ids", List.of(),
            "link_url", "",
            "cover_image", "",
            "title", title,
            "brief_content", extractBrief(content, 200),
            "content", content,
            "edit_type", 10, // 10 表示 Markdown
            "theme_ids", List.of()
        );

        return webClient.post()
            .uri("/article_draft/create")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map.class)
            .flatMap(response -> {
                String errMsg = (String) response.get("err_msg");
                Integer errNo = (Integer) response.get("err_no");

                if (errNo != null && errNo == 0) {
                    Map<String, Object> data = (Map<String, Object>) response.get("data");
                    if (data != null) {
                        String articleId = String.valueOf(data.get("article_id"));
                        return Mono.just(new PublishResult(
                            articleId,
                            "https://juejin.cn/post/" + articleId,
                            "草稿创建成功"
                        ));
                    }
                }

                return Mono.error(new RuntimeException("创建草稿失败 [" + errNo + "]: " + errMsg));
            });
    }

    /**
     * 更新草稿
     */
    private Mono<PublishResult> updateDraft(String cookie, String articleId, String content, String title) {
        Map<String, Object> requestBody = Map.of(
            "article_id", articleId,
            "category_id", "0",
            "tag_ids", List.of(),
            "link_url", "",
            "cover_image", "",
            "title", title,
            "brief_content", extractBrief(content, 200),
            "content", content,
            "edit_type", 10,
            "theme_ids", List.of()
        );

        return webClient.post()
            .uri("/article_draft/update")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map.class)
            .flatMap(response -> {
                String errMsg = (String) response.get("err_msg");
                Integer errNo = (Integer) response.get("err_no");

                if (errNo != null && errNo == 0) {
                    return Mono.just(new PublishResult(
                        articleId,
                        "https://juejin.cn/post/" + articleId,
                        "草稿更新成功"
                    ));
                }

                return Mono.error(new RuntimeException("更新草稿失败 [" + errNo + "]: " + errMsg));
            });
    }

    /**
     * 删除草稿
     */
    private Mono<Void> deleteDraft(String cookie, String articleId) {
        return webClient.post()
            .uri("/article_draft/delete")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("article_id", articleId))
            .retrieve()
            .bodyToMono(Map.class)
            .flatMap(response -> {
                String errMsg = (String) response.get("err_msg");
                Integer errNo = (Integer) response.get("err_no");

                if (errNo != null && errNo == 0) {
                    return Mono.empty();
                }

                return Mono.error(new RuntimeException("删除草稿失败 [" + errNo + "]: " + errMsg));
            });
    }

    /**
     * 提取摘要
     */
    private String extractBrief(String content, int maxLength) {
        if (content == null) return "";
        // 移除 Markdown 标记
        String brief = content
            .replaceAll("#+\\s*", "")
            .replaceAll("\\*+([^*]+)\\*+", "$1")
            .replaceAll("`+([^`]+)`+", "$1")
            .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1")
            .replaceAll("\n+", " ")
            .trim();
        return brief.length() > maxLength ? brief.substring(0, maxLength) + "..." : brief;
    }

    /**
     * 获取 Cookie
     */
    private String getCookie(SyncPlatform platform) {
        Map<String, String> credentials = platform.getSpec().getCredentials();
        return credentials != null ? credentials.get("cookie") : null;
    }
}
