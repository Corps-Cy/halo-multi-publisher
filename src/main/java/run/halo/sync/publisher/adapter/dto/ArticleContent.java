package run.halo.sync.publisher.adapter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文章内容 DTO - 用于平台适配器传输
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleContent {

    /**
     * 文章标题
     */
    private String title;

    /**
     * 文章摘要
     */
    private String summary;

    /**
     * 原始内容（Markdown）
     */
    private String rawContent;

    /**
     * HTML 内容（已转换）
     */
    private String htmlContent;

    /**
     * 封面图片 URL
     */
    private String coverImage;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 分类列表
     */
    private List<String> categories;

    /**
     * 作者名称
     */
    private String authorName;

    /**
     * 原文链接
     */
    private String sourceUrl;

    /**
     * Halo 文章 name
     */
    private String postName;

    /**
     * 文章 slug
     */
    private String slug;
}
