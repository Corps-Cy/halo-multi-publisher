package run.halo.sync.publisher.service;

import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;

/**
 * 文章获取服务
 * 从 Halo 获取文章内容
 */
public interface PostFetcher {

    /**
     * 根据文章 name 获取文章
     *
     * @param postName 文章 name
     * @return 文章对象
     */
    Mono<Post> fetchPost(String postName);

    /**
     * 根据文章 slug 获取文章
     *
     * @param slug 文章 slug
     * @return 文章对象
     */
    Mono<Post> fetchPostBySlug(String slug);
}
