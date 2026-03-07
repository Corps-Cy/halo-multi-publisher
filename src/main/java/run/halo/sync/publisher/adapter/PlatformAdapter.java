package run.halo.sync.publisher.adapter;

import reactor.core.publisher.Mono;
import run.halo.sync.publisher.extension.SyncPlatform;

/**
 * 平台适配器接口
 * 所有外部平台同步必须实现此接口
 */
public interface PlatformAdapter {

    /**
     * 获取支持的平台类型
     */
    SyncPlatform.PlatformType getPlatformType();

    /**
     * 发布文章到外部平台
     *
     * @param content 文章内容（Markdown 格式）
     * @param title 文章标题
     * @param platform 平台配置
     * @return 发布结果
     */
    Mono<PublishResult> publish(String content, String title, SyncPlatform platform);

    /**
     * 更新外部平台文章
     *
     * @param externalId 外部文章 ID
     * @param content 更新后的内容
     * @param title 更新后的标题
     * @param platform 平台配置
     * @return 更新结果
     */
    Mono<PublishResult> update(String externalId, String content, String title, SyncPlatform platform);

    /**
     * 删除外部平台文章
     *
     * @param externalId 外部文章 ID
     * @param platform 平台配置
     * @return 删除结果
     */
    Mono<Void> delete(String externalId, SyncPlatform platform);

    /**
     * 检查平台凭证是否有效
     *
     * @param platform 平台配置
     * @return 是否有效
     */
    Mono<Boolean> validateCredentials(SyncPlatform platform);

    /**
     * 发布结果
     */
    record PublishResult(
        String externalId,
        String externalUrl,
        String message
    ) {}
}
