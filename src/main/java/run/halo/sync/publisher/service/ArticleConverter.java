package run.halo.sync.publisher.service;

import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;
import run.halo.sync.publisher.adapter.dto.ArticleContent;

/**
 * 文章转换服务
 * 将 Halo Post 转换为平台适配器可用的 ArticleContent
 */
public interface ArticleConverter {

    /**
     * 将 Halo Post 转换为 ArticleContent
     *
     * @param post Halo 文章
     * @return 文章内容 DTO
     */
    Mono<ArticleContent> convert(Post post);
}
