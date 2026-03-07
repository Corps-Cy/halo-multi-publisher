package run.halo.sync.publisher.reconciler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;
import run.halo.app.core.extension.content.Post;
import run.halo.sync.publisher.extension.SyncPlatform;
import run.halo.sync.publisher.extension.SyncTask;

import java.util.Set;

/**
 * 监听 Post 发布事件，自动创建 SyncTask
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostPublishReconciler implements Reconciler<Reconciler.Request> {

    private final ReactiveExtensionClient client;

    @Override
    public Mono<Result> reconcile(Request request) {
        return client.fetch(Post.class, request.name())
            .filter(post -> isPublished(post) && needsSync(post))
            .flatMap(this::createSyncTasks)
            .thenReturn(Result.doNotRetry())
            .onErrorResume(e -> {
                log.error("Failed to handle post publish: {}", e.getMessage());
                return Mono.just(Result.doNotRetry());
            });
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
        // 通过 label 判断是否已同步
        Set<String> labels = post.getMetadata().getLabels();
        return labels == null || !labels.containsKey("sync.halo.run/synced");
    }

    /**
     * 为启用了自动同步的平台创建同步任务
     */
    private Mono<Void> createSyncTasks(Post post) {
        return client.listAll(SyncPlatform.class, null, null)
            .filter(platform -> Boolean.TRUE.equals(platform.getSpec().getEnabled()))
            .filter(platform -> platform.getSpec().getRules() != null
                && Boolean.TRUE.equals(platform.getSpec().getRules().getAutoSync()))
            .filter(platform -> matchesCategories(post, platform))
            .flatMap(platform -> createSyncTask(post, platform))
            .then();
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
        String categoryName = post.getSpec().getCategories();
        if (categoryName == null) {
            return false;
        }

        return rules.getCategories().contains(categoryName);
    }

    /**
     * 创建单个同步任务
     */
    private Mono<SyncTask> createSyncTask(Post post, SyncPlatform platform) {
        SyncTask task = new SyncTask();

        Metadata metadata = new Metadata();
        metadata.setName(generateTaskName(post.getMetadata().getName(), platform.getMetadata().getName()));
        task.setMetadata(metadata);

        SyncTask.Spec spec = new SyncTask.Spec();
        spec.setPostName(post.getMetadata().getName());
        spec.setPlatformName(platform.getMetadata().getName());
        spec.setAction(SyncTask.ActionType.CREATE);
        task.setSpec(spec);

        SyncTask.Status status = new SyncTask.Status();
        status.setPhase(SyncTask.PHASE_PENDING);
        task.setStatus(status);

        return client.create(task)
            .doOnSuccess(created -> log.info("Created SyncTask for post {} to platform {}",
                post.getMetadata().getName(), platform.getMetadata().getName()))
            .onErrorResume(e -> {
                // 任务可能已存在，忽略错误
                log.debug("SyncTask already exists or creation failed: {}", e.getMessage());
                return Mono.empty();
            });
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
            .extension(Post.class)
            .build();
    }
}
