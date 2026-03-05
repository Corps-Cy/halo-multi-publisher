package run.halo.sync.publisher.service.impl;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.sync.publisher.service.PostFetcher;

/**
 * 文章获取服务实现
 */
@Service
public class PostFetcherImpl implements PostFetcher {

    private final ReactiveExtensionClient client;

    public PostFetcherImpl(ReactiveExtensionClient client) {
        this.client = client;
    }

    @Override
    public Mono<Post> fetchPost(String postName) {
        return client.fetch(Post.class, postName)
            .switchIfEmpty(Mono.error(new RuntimeException(
                "Post not found: " + postName)));
    }

    @Override
    public Mono<Post> fetchPostBySlug(String slug) {
        ListOptions options = ListOptions.builder()
            .fieldSelector()
            .fieldEquals("spec.slug", slug)
            .end()
            .build();

        return client.listAll(Post.class, options, null)
            .next()
            .switchIfEmpty(Mono.error(new RuntimeException(
                "Post not found with slug: " + slug)));
    }
}
