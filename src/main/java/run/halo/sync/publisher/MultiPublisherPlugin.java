package run.halo.sync.publisher;

import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;
import run.halo.sync.publisher.extension.SyncPlatform;
import run.halo.sync.publisher.extension.SyncTask;

/**
 * Halo 多平台文章同步插件
 */
public class MultiPublisherPlugin extends BasePlugin {

    private final SchemeManager schemeManager;

    public MultiPublisherPlugin(PluginContext pluginContext, SchemeManager schemeManager) {
        super(pluginContext);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        // 注册 Extension Schemes
        schemeManager.register(SyncPlatform.class);
        schemeManager.register(SyncTask.class);
    }

    @Override
    public void stop() {
        // 插件停止时的清理逻辑
    }
}
