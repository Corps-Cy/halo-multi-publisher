package run.halo.sync.publisher.adapter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发布结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishResult {

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 外部平台文章 ID
     */
    private String externalId;

    /**
     * 外部平台文章链接
     */
    private String externalUrl;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 错误代码
     */
    private String errorCode;

    /**
     * 创建成功结果
     */
    public static PublishResult success(String externalId, String externalUrl) {
        return PublishResult.builder()
            .success(true)
            .externalId(externalId)
            .externalUrl(externalUrl)
            .build();
    }

    /**
     * 创建失败结果
     */
    public static PublishResult failure(String errorMessage) {
        return PublishResult.builder()
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }

    /**
     * 创建失败结果（带错误代码）
     */
    public static PublishResult failure(String errorCode, String errorMessage) {
        return PublishResult.builder()
            .success(false)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .build();
    }
}
