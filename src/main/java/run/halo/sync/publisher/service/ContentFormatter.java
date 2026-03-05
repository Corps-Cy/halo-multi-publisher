package run.halo.sync.publisher.service;

import reactor.core.publisher.Mono;
import run.halo.sync.publisher.adapter.dto.ArticleContent;

/**
 * 内容格式化服务
 * 将 Markdown 转换为各平台支持的 HTML 格式
 */
public interface ContentFormatter {

    /**
     * 格式化为微信支持的 HTML
     * 
     * @param content 原始内容
     * @return 格式化后的 HTML
     */
    Mono<String> formatForWeChat(ArticleContent content);

    /**
     * 格式化为掘金支持的格式
     * 
     * @param content 原始内容
     * @return 格式化后的内容
     */
    Mono<String> formatForJuejin(ArticleContent content);
}
