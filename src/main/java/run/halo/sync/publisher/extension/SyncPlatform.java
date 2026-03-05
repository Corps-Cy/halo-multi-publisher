package run.halo.sync.publisher.extension;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;
import run.halo.app.extension.Metadata;

/**
 * 同步平台配置 - 存储外部平台的连接信息和同步规则
 */
@GVK(
    group = "sync.halo.run",
    version = "v1alpha1",
    kind = "SyncPlatform",
    plural = "syncplatforms",
    singular = "syncplatform"
)
@Schema(description = "同步平台配置")
public class SyncPlatform extends AbstractExtension {

    public static final String GROUP = "sync.halo.run";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "SyncPlatform";

    @Schema(description = "期望状态")
    private SyncPlatformSpec spec;

    @Schema(description = "实际状态")
    private SyncPlatformStatus status;

    public SyncPlatform() {
        this.setMetadata(new Metadata());
        this.status = new SyncPlatformStatus();
    }

    public SyncPlatformSpec getSpec() {
        return spec;
    }

    public void setSpec(SyncPlatformSpec spec) {
        this.spec = spec;
    }

    public SyncPlatformStatus getStatus() {
        return status;
    }

    public void setStatus(SyncPlatformStatus status) {
        this.status = status;
    }

    /**
     * 平台类型枚举
     */
    public enum PlatformType {
        @JsonProperty("wechat")
        WECHAT("wechat", "微信公众号"),
        
        @JsonProperty("juejin")
        JUEJIN("juejin", "掘金"),
        
        @JsonProperty("zhihu")
        ZHIHU("zhihu", "知乎"),
        
        @JsonProperty("toutiao")
        TOUTIAO("toutiao", "今日头条");

        private final String code;
        private final String displayName;

        PlatformType(String code, String displayName) {
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
     * 同步规则配置
     */
    @Schema(description = "同步规则")
    public static class SyncRules {
        @Schema(description = "发布时自动同步")
        private Boolean autoSync = false;

        @Schema(description = "仅同步指定分类（为空则同步全部）")
        private List<String> categories;

        @Schema(description = "是否转换 Markdown 格式")
        private Boolean convertMarkdown = true;

        @Schema(description = "是否包含封面图")
        private Boolean includeCover = true;

        @Schema(description = "同步失败时是否重试")
        private Boolean retryOnFailure = true;

        @Schema(description = "最大重试次数")
        private Integer maxRetries = 3;

        // Getters and Setters
        public Boolean getAutoSync() {
            return autoSync;
        }

        public void setAutoSync(Boolean autoSync) {
            this.autoSync = autoSync;
        }

        public List<String> getCategories() {
            return categories;
        }

        public void setCategories(List<String> categories) {
            this.categories = categories;
        }

        public Boolean getConvertMarkdown() {
            return convertMarkdown;
        }

        public void setConvertMarkdown(Boolean convertMarkdown) {
            this.convertMarkdown = convertMarkdown;
        }

        public Boolean getIncludeCover() {
            return includeCover;
        }

        public void setIncludeCover(Boolean includeCover) {
            this.includeCover = includeCover;
        }

        public Boolean getRetryOnFailure() {
            return retryOnFailure;
        }

        public void setRetryOnFailure(Boolean retryOnFailure) {
            this.retryOnFailure = retryOnFailure;
        }

        public Integer getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
        }
    }

    /**
     * Spec - 期望状态
     */
    @Schema(description = "同步平台期望状态")
    public static class SyncPlatformSpec {
        @Schema(description = "平台类型")
        private PlatformType platformType;

        @Schema(description = "显示名称")
        private String displayName;

        @Schema(description = "平台凭证（加密存储）")
        private Map<String, String> credentials;

        @Schema(description = "是否启用")
        private Boolean enabled = true;

        @Schema(description = "同步规则")
        private SyncRules rules = new SyncRules();

        // Getters and Setters
        public PlatformType getPlatformType() {
            return platformType;
        }

        public void setPlatformType(PlatformType platformType) {
            this.platformType = platformType;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public Map<String, String> getCredentials() {
            return credentials;
        }

        public void setCredentials(Map<String, String> credentials) {
            this.credentials = credentials;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public SyncRules getRules() {
            return rules;
        }

        public void setRules(SyncRules rules) {
            this.rules = rules;
        }
    }

    /**
     * Status - 实际状态
     */
    @Schema(description = "同步平台实际状态")
    public static class SyncPlatformStatus {
        @Schema(description = "连接状态")
        private Boolean connected = false;

        @Schema(description = "最后同步时间")
        private String lastSyncTime;

        @Schema(description = "总同步文章数")
        private Integer totalSynced = 0;

        @Schema(description = "状态消息")
        private String message;

        // Getters and Setters
        public Boolean getConnected() {
            return connected;
        }

        public void setConnected(Boolean connected) {
            this.connected = connected;
        }

        public String getLastSyncTime() {
            return lastSyncTime;
        }

        public void setLastSyncTime(String lastSyncTime) {
            this.lastSyncTime = lastSyncTime;
        }

        public Integer getTotalSynced() {
            return totalSynced;
        }

        public void setTotalSynced(Integer totalSynced) {
            this.totalSynced = totalSynced;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
