package run.halo.sync.publisher.extension;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "sync.halo.run", version = "v1alpha1",
     kind = "SyncPlatform", plural = "syncplatforms", singular = "syncplatform")
public class SyncPlatform extends AbstractExtension {

    public static final String GROUP = "sync.halo.run";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "SyncPlatform";

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    private Status status;

    @Data
    public static class Spec {
        @Schema(description = "平台类型", requiredMode = Schema.RequiredMode.REQUIRED)
        private PlatformType platformType;

        @Schema(description = "显示名称", requiredMode = Schema.RequiredMode.REQUIRED)
        private String displayName;

        @Schema(description = "凭证信息")
        private Map<String, String> credentials;

        @Schema(description = "是否启用")
        private Boolean enabled = true;

        @Schema(description = "同步规则")
        private SyncRules rules;
    }

    @Data
    public static class Status {
        @Schema(description = "平台状态")
        private String phase;

        @Schema(description = "最后同步时间")
        private String lastSyncTime;

        @Schema(description = "错误信息")
        private String errorMessage;
    }

    @Data
    public static class SyncRules {
        @Schema(description = "是否自动同步")
        private Boolean autoSync = false;

        @Schema(description = "分类过滤（空表示全部）")
        private List<String> categories;

        @Schema(description = "标签过滤（空表示全部）")
        private List<String> tags;
    }

    public enum PlatformType {
        WECHAT,
        JUEJIN
    }
}
