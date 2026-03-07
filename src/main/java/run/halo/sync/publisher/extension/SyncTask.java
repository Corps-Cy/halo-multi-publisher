package run.halo.sync.publisher.extension;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "sync.halo.run", version = "v1alpha1",
     kind = "SyncTask", plural = "synctasks", singular = "synctask")
public class SyncTask extends AbstractExtension {

    public static final String GROUP = "sync.halo.run";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "SyncTask";

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    private Status status;

    @Data
    public static class Spec {
        @Schema(description = "文章名称", requiredMode = Schema.RequiredMode.REQUIRED)
        private String postName;

        @Schema(description = "平台名称", requiredMode = Schema.RequiredMode.REQUIRED)
        private String platformName;

        @Schema(description = "操作类型", requiredMode = Schema.RequiredMode.REQUIRED)
        private ActionType action;
    }

    @Data
    public static class Status {
        @Schema(description = "任务阶段")
        private String phase;

        @Schema(description = "外部平台文章 ID")
        private String externalId;

        @Schema(description = "外部平台文章链接")
        private String externalUrl;

        @Schema(description = "重试次数")
        private Integer retryCount = 0;

        @Schema(description = "错误信息")
        private String errorMessage;

        @Schema(description = "开始时间")
        private String startTime;

        @Schema(description = "完成时间")
        private String completionTime;
    }

    public enum ActionType {
        CREATE,
        UPDATE,
        DELETE
    }

    public static final String PHASE_PENDING = "Pending";
    public static final String PHASE_RUNNING = "Running";
    public static final String PHASE_SUCCESS = "Success";
    public static final String PHASE_FAILED = "Failed";
}
