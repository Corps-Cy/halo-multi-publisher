package run.halo.sync.publisher;

import org.springframework.stereotype.Component;
import run.halo.app.extension.SchemeManager;
import run.halo.sync.publisher.extension.SyncPlatform;
import run.halo.sync.publisher.extension.SyncTask;

import jakarta.annotation.PostConstruct;

/**
 * Halo Multi-Publisher Plugin
 * 将 Halo 文章同步到多个外部平台
 */
@Component
public class HaloMultiPublisherPlugin {

    private final SchemeManager schemeManager;

    public HaloMultiPublisherPlugin(SchemeManager schemeManager) {
        this.schemeManager = schemeManager;
    }

    @PostConstruct
    public void registerExtensions() {
        // Register Extension schemes
        schemeManager.register(SyncPlatform.class);
        schemeManager.register(SyncTask.class);
    }
}
