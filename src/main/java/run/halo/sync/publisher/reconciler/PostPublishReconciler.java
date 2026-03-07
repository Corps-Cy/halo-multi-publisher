package run.halo.sync.publisher.reconciler;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;
import run.halo.app.core.extension.content.Post;
import run.halo.sync.publisher.extension.SyncPlatform;
import run.halo.sync.publisher.extension.SyncTask;

/**
 * 监听 Post 发布事件，自动创建 SyncTask
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostPublishReconciler implements Reconciler<Reconciler.Request> {

    private final ExtensionClient client;

    @Override
    public Result reconcile(Request request) {
        Optional<Post> postOpt = client.fetch(Post.class, request.name());
        if (postOpt.isEmpty()) {
            return Result.doNotRetry();
        }

        Post post = postOpt.get();

        // 检查是否已发布且需要同步
        if (!isPublished(post) || !needsSync(post)) {
            return Result.doNotRetry();
        }

        try {
            createSyncTasks(post);
            return Result.doNotRetry();
        } catch (Exception e) {
            log.error("Failed to handle post publish: {}", e.getMessage());
            return Result.doNotRetry();
        }
    }

    /**
     * 检查文章是否已发布
     */
    private boolean isPublished(Post post) {
        return post.getSpec().getPublish() != null
            && post.getSpec().getPublish()
            && post.getStatus() != null
            && "PUBLISHED".equals(post.getStatus().getPhase());
    }

    /**
     * 检查文章是否需要同步（未同步过）
     */
    private boolean needsSync(Post post) {
        Map<String, String> labels = post.getMetadata().getLabels();
        return labels == null || !labels.containsKey("sync.halo.run/synced");
    }

    /**
     * 为启用了自动同步的平台创建同步任务
     */
    private void createSyncTasks(Post post) {
        List<SyncPlatform> platforms = client.listAll(SyncPlatform.class, null, null);

        for (SyncPlatform platform : platforms) {
            if (!Boolean.TRUE.equals(platform.getSpec().getEnabled())) {
                continue;
            }
            if (platform.getSpec().getRules() == null
                || !Boolean.TRUE.equals(platform.getSpec().getRules().getAutoSync())) {
                continue;
            }
            if (!matchesCategories(post, platform)) {
                continue;
            }

            createSyncTask(post, platform);
        }
    }

    /**
     * 检查文章分类是否匹配平台配置
     */
    private boolean matchesCategories(Post post, SyncPlatform platform) {
        SyncPlatform.SyncRules rules = platform.getSpec().getRules();
        if (rules == null || rules.getCategories() == null || rules.getCategories().isEmpty()) {
            return true; // 未配置分类过滤，匹配所有文章
        }

        // 获取文章的分类
        List<String> postCategories = post.getSpec().getCategories();
        if (postCategories == null || postCategories.isEmpty()) {
            return false;
        }

        // 检查是否有交集
        return postCategories.stream()
            .anyMatch(cat -> rules.getCategories().contains(cat));
    }

    /**
     * 创建单个同步任务
     */
    private void createSyncTask(Post post, SyncPlatform platform) {
        String taskName = generateTaskName(post.getMetadata().getName(), platform.getMetadata().getName());

        // 检查任务是否已存在
        if (client.fetch(SyncTask.class, taskName).isPresent()) {
            log.debug("SyncTask {} already exists", taskName);
            return;
        }

        SyncTask task = new SyncTask();

        Metadata metadata = new Metadata();
        metadata.setName(taskName);
        task.setMetadata(metadata);

        SyncTask.Spec spec = new SyncTask.Spec();
        spec.setPostName(post.getMetadata().getName());
        spec.setPlatformName(platform.getMetadata().getName());
        spec.setAction(SyncTask.ActionType.CREATE);
        task.setSpec(spec);

        SyncTask.Status status = new SyncTask.Status();
        status.setPhase(SyncTask.PHASE_PENDING);
        task.setStatus(status);

        client.create(task);
        log.info("Created SyncTask for post {} to platform {}",
            post.getMetadata().getName(), platform.getMetadata().getName());
    }

    /**
     * 生成任务名称
     */
    private String generateTaskName(String postName, String platformName) {
        return postName + "-to-" + platformName;
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder
            .extension(new Post())
            .build();
    }
}
