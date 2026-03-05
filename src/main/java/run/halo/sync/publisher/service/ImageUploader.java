package run.halo.sync.publisher.service;

import reactor.core.publisher.Mono;
import run.halo.sync.publisher.extension.SyncPlatform;

/**
 * 图片上传服务
 * 将图片上传到各平台并返回可用的 URL 或 media_id
 */
public interface ImageUploader {

    /**
     * 上传图片到微信公众号
     * 
     * @param imageUrl 图片 URL（支持 http/https）
     * @param platform 平台配置
     * @return 微信素材 media_id
     */
    Mono<String> uploadToWeChat(String imageUrl, SyncPlatform platform);

    /**
     * 下载图片并上传（支持本地文件路径）
     * 
     * @param imageSource 图片来源（URL 或本地路径）
     * @param platform 平台配置
     * @return 上传后的 URL 或 media_id
     */
    Mono<UploadResult> uploadImage(String imageSource, SyncPlatform platform);

    /**
     * 上传结果
     */
    record UploadResult(
        String url,          // 图片 URL
        String mediaId,      // 微信 media_id
        String thumbnailUrl  // 缩略图 URL
    ) {}
}
