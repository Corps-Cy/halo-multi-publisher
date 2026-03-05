package run.halo.sync.publisher.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.halo.sync.publisher.extension.SyncPlatform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信 Access Token 管理器
 * 
 * 功能：
 * - 自动获取和缓存 Access Token
 * - 提前刷新机制（提前 5 分钟）
 * - 多账号隔离
 */
@Slf4j
@Component
public class WeChatTokenManager {

    private static final String WECHAT_API_BASE = "https://api.weixin.qq.com/cgi-bin";
    private static final long TOKEN_EXPIRE_MARGIN_MS = 5 * 60 * 1000; // 5 分钟

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Map<String, TokenCache> tokenCache = new ConcurrentHashMap<>();

    public WeChatTokenManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
            .baseUrl(WECHAT_API_BASE)
            .build();
    }

    /**
     * 获取 Access Token（带缓存）
     */
    public Mono<String> getAccessToken(SyncPlatform platform) {
        String cacheKey = getCacheKey(platform);

        // 检查缓存
        TokenCache cached = tokenCache.get(cacheKey);
        if (cached != null && cached.isValid()) {
            log.debug("Using cached access token for: {}", cacheKey);
            return Mono.just(cached.token);
        }

        // 获取新 token
        return fetchNewToken(platform, cacheKey);
    }

    /**
     * 清除缓存的 Token
     */
    public void invalidateToken(SyncPlatform platform) {
        String cacheKey = getCacheKey(platform);
        tokenCache.remove(cacheKey);
        log.info("Token cache invalidated for: {}", cacheKey);
    }

    // ==================== 私有方法 ====================

    private Mono<String> fetchNewToken(SyncPlatform platform, String cacheKey) {
        Map<String, String> credentials = platform.getSpec().getCredentials();
        
        if (credentials == null) {
            return Mono.error(new RuntimeException("WeChat credentials not configured"));
        }

        String appId = credentials.get("appId");
        String appSecret = credentials.get("appSecret");

        if (appId == null || appSecret == null) {
            return Mono.error(new RuntimeException("WeChat appId or appSecret not configured"));
        }

        log.info("Fetching new access token for appId: {}", appId);

        return webClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/token")
                .queryParam("grant_type", "client_credential")
                .queryParam("appid", appId)
                .queryParam("secret", appSecret)
                .build())
            .retrieve()
            .bodyToMono(String.class)
            .flatMap(response -> parseAndCacheToken(response, cacheKey));
    }

    private Mono<String> parseAndCacheToken(String response, String cacheKey) {
        try {
            JsonNode json = objectMapper.readTree(response);

            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                String errmsg = json.get("errmsg").asText();
                int errcode = json.get("errcode").asInt();
                return Mono.error(new RuntimeException(
                    "WeChat API error: " + errmsg + " (code: " + errcode + ")"));
            }

            String token = json.get("access_token").asText();
            int expiresIn = json.get("expires_in").asInt();
            
            // 计算过期时间（提前 5 分钟）
            long expiresAt = System.currentTimeMillis() + 
                (expiresIn * 1000L) - TOKEN_EXPIRE_MARGIN_MS;

            // 更新缓存
            tokenCache.put(cacheKey, new TokenCache(token, expiresAt));

            log.info("✅ Access token obtained and cached, expires in {}s", expiresIn);

            return Mono.just(token);
        } catch (Exception e) {
            log.error("Failed to parse token response", e);
            return Mono.error(new RuntimeException("Failed to parse token response", e));
        }
    }

    private String getCacheKey(SyncPlatform platform) {
        Map<String, String> credentials = platform.getSpec().getCredentials();
        return credentials != null ? 
            "wechat:" + credentials.getOrDefault("appId", platform.getMetadata().getName()) :
            "wechat:" + platform.getMetadata().getName();
    }

    // ==================== 内部类 ====================

    private static class TokenCache {
        final String token;
        final long expiresAt;

        TokenCache(String token, long expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }

        boolean isValid() {
            return System.currentTimeMillis() < expiresAt;
        }
    }
}
