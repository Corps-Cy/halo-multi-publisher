package run.halo.sync.publisher.adapter;

import reactor.core.publisher.Mono;
import run.halo.sync.publisher.adapter.dto.ArticleContent;
import run.halo.sync.publisher.adapter.dto.PublishResult;
import run.halo.sync.publisher.extension.SyncPlatform;

/**
 * 平台适配器接口
 * 所有外部平台的同步适配器都需要实现此接口
 */
public interface PlatformAdapter {

    /**
     * 获取平台类型
     */
    SyncPlatform.PlatformType getPlatformType();

    /**
     * 发布文章到平台
     *
     * @param content 文章内容
     * @param platform 平台配置
     * @return 发布结果
     */
    Mono<PublishResult> publish(ArticleContent content, SyncPlatform platform);

    /**
     * 更新文章
     *
     * @param externalId 外部平台文章 ID
     * @param content 文章内容
     * @param platform 平台配置
     * @return 发布结果
     */
    Mono<PublishResult> update(String externalId, ArticleContent content, SyncPlatform platform);

    /**
     * 删除文章
     *
     * @param externalId 外部平台文章 ID
     * @param platform 平台配置
     * @return 删除结果
     */
    Mono<Void> delete(String externalId, SyncPlatform platform);

    /**
     * 测试连接
     *
     * @param platform 平台配置
     * @return 是否连接成功
     */
    Mono<Boolean> testConnection(SyncPlatform platform);
}
