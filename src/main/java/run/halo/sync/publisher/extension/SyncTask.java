package run.halo.sync.publisher.extension;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;
import run.halo.app.extension.Metadata;

/**
 * 同步任务 - 记录单次同步操作的状态
 */
@GVK(
    group = "sync.halo.run",
    version = "v1alpha1",
    kind = "SyncTask",
    plural = "synctasks",
    singular = "synctask"
)
@Schema(description = "同步任务")
public class SyncTask extends AbstractExtension {

    public static final String GROUP = "sync.halo.run";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "SyncTask";

    @Schema(description = "期望状态")
    private SyncTaskSpec spec;

    @Schema(description = "实际状态")
    private SyncTaskStatus status;

    public SyncTask() {
        this.setMetadata(new Metadata());
        this.status = new SyncTaskStatus();
    }

    public SyncTaskSpec getSpec() {
        return spec;
    }

    public void setSpec(SyncTaskSpec spec) {
        this.spec = spec;
    }

    public SyncTaskStatus getStatus() {
        return status;
    }

    public void setStatus(SyncTaskStatus status) {
        this.status = status;
    }

    /**
     * 同步动作类型
     */
    public enum SyncAction {
        @JsonProperty("create")
        CREATE("create", "创建"),
        
        @JsonProperty("update")
        UPDATE("update", "更新"),
        
        @JsonProperty("delete")
        DELETE("delete", "删除");

        private final String code;
        private final String displayName;

        SyncAction(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String getCode() {
            return code;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 同步阶段
     */
    public enum SyncPhase {
        @JsonProperty("pending")
        PENDING("pending", "等待中"),
        
        @JsonProperty("running")
        RUNNING("running", "执行中"),
        
        @JsonProperty("success")
        SUCCESS("success", "成功"),
        
        @JsonProperty("failed")
        FAILED("failed", "失败"),
        
        @JsonProperty("cancelled")
        CANCELLED("cancelled", "已取消");

        private final String code;
        private final String displayName;

        SyncPhase(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String getCode() {
            return code;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Spec - 期望状态
     */
    @Schema(description = "同步任务期望状态")
    public static class SyncTaskSpec {
        @Schema(description = "Halo 文章 name")
        private String postName;

        @Schema(description = "目标平台 name")
        private String platformName;

        @Schema(description = "同步动作")
        private SyncAction action = SyncAction.CREATE;

        @Schema(description = "是否立即执行（跳过队列）")
        private Boolean immediate = false;

        // Getters and Setters
        public String getPostName() {
            return postName;
        }

        public void setPostName(String postName) {
            this.postName = postName;
        }

        public String getPlatformName() {
            return platformName;
        }

        public void setPlatformName(String platformName) {
            this.platformName = platformName;
        }

        public SyncAction getAction() {
            return action;
        }

        public void setAction(SyncAction action) {
            this.action = action;
        }

        public Boolean getImmediate() {
            return immediate;
        }

        public void setImmediate(Boolean immediate) {
            this.immediate = immediate;
        }
    }

    /**
     * Status - 实际状态
     */
    @Schema(description = "同步任务实际状态")
    public static class SyncTaskStatus {
        @Schema(description = "同步阶段")
        private SyncPhase phase = SyncPhase.PENDING;

        @Schema(description = "外部平台文章 ID")
        private String externalId;

        @Schema(description = "外部平台文章链接")
        private String externalUrl;

        @Schema(description = "状态消息")
        private String message;

        @Schema(description = "重试次数")
        private Integer retryCount = 0;

        @Schema(description = "开始时间")
        private String startTime;

        @Schema(description = "完成时间")
        private String completionTime;

        // Getters and Setters
        public SyncPhase getPhase() {
            return phase;
        }

        public void setPhase(SyncPhase phase) {
            this.phase = phase;
        }

        public String getExternalId() {
            return externalId;
        }

        public void setExternalId(String externalId) {
            this.externalId = externalId;
        }

        public String getExternalUrl() {
            return externalUrl;
        }

        public void setExternalUrl(String externalUrl) {
            this.externalUrl = externalUrl;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Integer getRetryCount() {
            return retryCount;
        }

        public void setRetryCount(Integer retryCount) {
            this.retryCount = retryCount;
        }

        public String getStartTime() {
            return startTime;
        }

        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }

        public String getCompletionTime() {
            return completionTime;
        }

        public void setCompletionTime(String completionTime) {
            this.completionTime = completionTime;
        }
    }
}
