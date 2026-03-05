package run.halo.sync.publisher.service.impl;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.sync.publisher.adapter.dto.ArticleContent;
import run.halo.sync.publisher.service.ArticleConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * 文章转换服务实现
 */
@Service
public class ArticleConverterImpl implements ArticleConverter {

    private final ReactiveExtensionClient client;

    public ArticleConverterImpl(ReactiveExtensionClient client) {
        this.client = client;
    }

    @Override
    public Mono<ArticleContent> convert(Post post) {
        ArticleContent.ArticleContentBuilder builder = ArticleContent.builder()
            .title(post.getSpec().getTitle())
            .slug(post.getSpec().getSlug())
            .postName(post.getMetadata().getName());

        // 获取原始内容
        if (post.getContent() != null) {
            builder.rawContent(post.getContent().getRaw());
            builder.htmlContent(post.getContent().getContent());
        }

        // 获取摘要
        if (post.getSpec().getExcerpt() != null) {
            builder.summary(post.getSpec().getExcerpt());
        }

        // 获取标签
        List<String> tags = new ArrayList<>();
        if (post.getSpec().getTags() != null) {
            tags.addAll(post.getSpec().getTags());
        }
        builder.tags(tags);

        // 获取分类
        List<String> categories = new ArrayList<>();
        if (post.getSpec().getCategories() != null) {
            categories.addAll(post.getSpec().getCategories());
        }
        builder.categories(categories);

        // 获取封面图
        if (post.getSpec().getCover() != null) {
            builder.coverImage(post.getSpec().getCover());
        }

        // 原文链接（需要配置站点 URL）
        // TODO: 从配置中获取站点 URL
        builder.sourceUrl("");

        return Mono.just(builder.build());
    }
}
