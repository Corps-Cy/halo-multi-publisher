package run.halo.sync.publisher.reconciler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.core.extension.content.Post;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;
import run.halo.sync.publisher.extension.SyncPlatform;
import run.halo.sync.publisher.extension.SyncTask;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 文章发布监听器
 * 
 * 监听 Halo Post 发布事件，自动创建同步任务
 */
@Slf4j
@Component
public class PostPublishReconciler implements Reconciler<Reconciler.Request> {

    private final ReactiveExtensionClient client;

    public PostPublishReconciler(ReactiveExtensionClient client) {
        this.client = client;
    }

    @Override
    public Result reconcile(Request request) {
        return client.fetch(Post.class, request.name())
            .flatMap(post -> {
                // 只处理已发布的文章
                if (!isPublished(post)) {
                    return Mono.empty();
                }

                // 检查是否需要自动同步
                return shouldAutoSync(post)
                    .flatMap(platforms -> {
                        if (platforms.isEmpty()) {
                            log.debug("No platforms need auto sync for post: {}", request.name());
                            return Mono.empty();
                        }

                        // 为每个启用了自动同步的平台创建同步任务
                        return createSyncTasks(post, platforms);
                    });
            })
            .onErrorResume(e -> {
                log.error("Error in PostPublishReconciler for: {}", request.name(), e);
                return Mono.empty();
            })
            .then(Mono.just(Result.doNotRetry()));
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder
            .extension(Post.class)
            .build();
    }

    /**
     * 检查文章是否已发布
     */
    private boolean isPublished(Post post) {
        return post.isPublished() && 
               post.getSpec().getPublish() &&
               post.getStatus() != null && 
               "PUBLISHED".equals(post.getStatus().getPhase());
    }

    /**
     * 检查是否需要自动同步
     * 返回需要自动同步的平台列表
     */
    private Mono<List<SyncPlatform>> shouldAutoSync(Post post) {
        return client.listAll(SyncPlatform.class, null, null)
            .filter(platform -> {
                // 平台必须启用
                if (!platform.getSpec().getEnabled()) {
                    return false;
                }

                // 检查自动同步配置
                SyncPlatform.SyncRules rules = platform.getSpec().getRules();
                if (rules == null || !Boolean.TRUE.equals(rules.getAutoSync())) {
                    return false;
                }

                // 检查分类过滤
                List<String> allowedCategories = rules.getCategories();
                if (allowedCategories != null && !allowedCategories.isEmpty()) {
                    List<String> postCategories = post.getSpec().getCategories();
                    if (postCategories == null || postCategories.isEmpty()) {
                        return false;
                    }
                    
                    // 检查文章分类是否在允许列表中
                    return postCategories.stream()
                        .anyMatch(allowedCategories::contains);
                }

                return true;
            })
            .collectList();
    }

    /**
     * 为文章创建同步任务
     */
    private Mono<Void> createSyncTasks(Post post, List<SyncPlatform> platforms) {
        log.info("Creating sync tasks for post: {} to {} platforms", 
            post.getMetadata().getName(), platforms.size());

        return Mono.whenDelayError(
            platforms.stream()
                .map(platform -> createSyncTask(post, platform))
                .toArray(Mono[]::new)
        );
    }

    /**
     * 创建单个同步任务
     */
    private Mono<Void> createSyncTask(Post post, SyncPlatform platform) {
        SyncTask task = new SyncTask();
        
        // 设置 metadata
        task.getMetadata().setName(generateTaskName(post, platform));
        task.getMetadata().setAnnotations(new java.util.HashMap<>());
        task.getMetadata().getAnnotations().put("sync.halo.run/post-name", post.getMetadata().getName());
        task.getMetadata().getAnnotations().put("sync.halo.run/platform-name", platform.getMetadata().getName());
        task.getMetadata().getAnnotations().put("sync.halo.run/created-by", "auto-sync");
        
        // 设置 spec
        SyncTask.SyncTaskSpec spec = new SyncTask.SyncTaskSpec();
        spec.setPostName(post.getMetadata().getName());
        spec.setPlatformName(platform.getMetadata().getName());
        spec.setAction(SyncTask.SyncAction.CREATE);
        spec.setImmediate(false);
        task.setSpec(spec);
        
        // 初始化 status
        SyncTask.SyncTaskStatus status = task.getStatus();
        status.setPhase(SyncTask.SyncPhase.PENDING);
        status.setMessage("Auto created by post publish event");
        
        return client.create(task)
            .doOnSuccess(t -> log.info("Created sync task: {} for platform: {}", 
                t.getMetadata().getName(), platform.getMetadata().getName()))
            .onErrorResume(e -> {
                // 忽略已存在的任务
                if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                    log.debug("Sync task already exists for post: {}, platform: {}", 
                        post.getMetadata().getName(), platform.getMetadata().getName());
                    return Mono.empty();
                }
                log.error("Failed to create sync task", e);
                return Mono.empty();
            })
            .then();
    }

    /**
     * 生成任务名称
     */
    private String generateTaskName(Post post, SyncPlatform platform) {
        return String.format("%s-to-%s-%s",
            post.getMetadata().getName(),
            platform.getMetadata().getName(),
            UUID.randomUUID().toString().substring(0, 8));
    }
}
