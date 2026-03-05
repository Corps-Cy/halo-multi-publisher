package run.halo.sync.publisher.reconciler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;
import run.halo.sync.publisher.adapter.PlatformAdapter;
import run.halo.sync.publisher.adapter.dto.ArticleContent;
import run.halo.sync.publisher.adapter.dto.PublishResult;
import run.halo.sync.publisher.extension.SyncPlatform;
import run.halo.sync.publisher.extension.SyncTask;
import run.halo.sync.publisher.service.ArticleConverter;
import run.halo.sync.publisher.service.PostFetcher;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 同步任务协调器
 * 监听 SyncTask 变化并执行同步逻辑
 */
@Slf4j
@Component
public class SyncTaskReconciler implements Reconciler<Reconciler.Request> {

    private final ReactiveExtensionClient client;
    private final PostFetcher postFetcher;
    private final ArticleConverter articleConverter;
    private final Map<SyncPlatform.PlatformType, PlatformAdapter> adapterMap;

    public SyncTaskReconciler(
            ReactiveExtensionClient client,
            PostFetcher postFetcher,
            ArticleConverter articleConverter,
            List<PlatformAdapter> adapters) {
        this.client = client;
        this.postFetcher = postFetcher;
        this.articleConverter = articleConverter;
        
        // Build adapter map
        this.adapterMap = new ConcurrentHashMap<>();
        for (PlatformAdapter adapter : adapters) {
            adapterMap.put(adapter.getPlatformType(), adapter);
            log.info("Registered platform adapter: {}", adapter.getPlatformType());
        }
    }

    @Override
    public Result reconcile(Request request) {
        log.debug("Reconciling SyncTask: {}", request.name());

        return client.fetch(SyncTask.class, request.name())
            .flatMap(this::processSyncTask)
            .onErrorResume(e -> {
                log.error("Error reconciling SyncTask: {}", request.name(), e);
                return handleReconcileError(request.name(), e);
            })
            .then(Mono.just(Result.doNotRetry()));
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder
            .extension(SyncTask.class)
            .build();
    }

    /**
     * 处理同步任务
     */
    private Mono<Void> processSyncTask(SyncTask task) {
        SyncTask.SyncTaskStatus status = task.getStatus();

        // 检查任务状态
        if (status.getPhase() == SyncTask.SyncPhase.SUCCESS ||
            status.getPhase() == SyncTask.SyncPhase.CANCELLED) {
            log.debug("Task already completed: {}, phase: {}", 
                task.getMetadata().getName(), status.getPhase());
            return Mono.empty();
        }

        // 检查是否需要重试
        if (status.getPhase() == SyncTask.SyncPhase.FAILED) {
            // TODO: 实现重试逻辑
            return Mono.empty();
        }

        // 更新状态为执行中
        status.setPhase(SyncTask.SyncPhase.RUNNING);
        status.setStartTime(Instant.now().toString());

        return client.update(task)
            .flatMap(this::executeSyncTask)
            .flatMap(result -> updateTaskWithResult(task, result))
            .then();
    }

    /**
     * 执行同步任务
     */
    private Mono<PublishResult> executeSyncTask(SyncTask task) {
        String postName = task.getSpec().getPostName();
        String platformName = task.getSpec().getPlatformName();
        SyncTask.SyncAction action = task.getSpec().getAction();

        // 获取平台配置
        return client.fetch(SyncPlatform.class, platformName)
            .switchIfEmpty(Mono.error(new RuntimeException(
                "Platform not found: " + platformName)))
            .flatMap(platform -> {
                // 检查平台是否启用
                if (!platform.getSpec().getEnabled()) {
                    return Mono.just(PublishResult.failure(
                        "PLATFORM_DISABLED",
                        "Platform is disabled: " + platformName
                    ));
                }

                // 获取适配器
                PlatformAdapter adapter = adapterMap.get(platform.getSpec().getPlatformType());
                if (adapter == null) {
                    return Mono.just(PublishResult.failure(
                        "ADAPTER_NOT_FOUND",
                        "No adapter for platform: " + platform.getSpec().getPlatformType()
                    ));
                }

                // 获取文章内容并执行同步
                return fetchAndConvertPost(postName)
                    .flatMap(content -> executeAction(adapter, action, content, platform, task));
            });
    }

    /**
     * 获取并转换文章内容
     */
    private Mono<ArticleContent> fetchAndConvertPost(String postName) {
        return postFetcher.fetchPost(postName)
            .flatMap(articleConverter::convert);
    }

    /**
     * 执行同步动作
     */
    private Mono<PublishResult> executeAction(
            PlatformAdapter adapter,
            SyncTask.SyncAction action,
            ArticleContent content,
            SyncPlatform platform,
            SyncTask task) {
        
        switch (action) {
            case CREATE:
                return adapter.publish(content, platform);
            
            case UPDATE:
                String externalId = task.getStatus().getExternalId();
                if (externalId == null) {
                    return Mono.just(PublishResult.failure(
                        "NO_EXTERNAL_ID",
                        "Cannot update: no external ID found"
                    ));
                }
                return adapter.update(externalId, content, platform);
            
            case DELETE:
                externalId = task.getStatus().getExternalId();
                if (externalId == null) {
                    return Mono.just(PublishResult.failure(
                        "NO_EXTERNAL_ID",
                        "Cannot delete: no external ID found"
                    ));
                }
                return adapter.delete(externalId, platform)
                    .thenReturn(PublishResult.success(null, null));
            
            default:
                return Mono.just(PublishResult.failure(
                    "UNKNOWN_ACTION",
                    "Unknown action: " + action
                ));
        }
    }

    /**
     * 更新任务状态
     */
    private Mono<SyncTask> updateTaskWithResult(SyncTask task, PublishResult result) {
        return client.fetch(SyncTask.class, task.getMetadata().getName())
            .flatMap(latestTask -> {
                SyncTask.SyncTaskStatus status = latestTask.getStatus();
                
                if (result.getSuccess()) {
                    status.setPhase(SyncTask.SyncPhase.SUCCESS);
                    status.setExternalId(result.getExternalId());
                    status.setExternalUrl(result.getExternalUrl());
                    status.setMessage("Sync completed successfully");
                } else {
                    status.setPhase(SyncTask.SyncPhase.FAILED);
                    status.setMessage(result.getErrorMessage());
                    status.setRetryCount(status.getRetryCount() + 1);
                }
                
                status.setCompletionTime(Instant.now().toString());
                
                return client.update(latestTask);
            });
    }

    /**
     * 处理协调错误
     */
    private Mono<Void> handleReconcileError(String taskName, Throwable error) {
        return client.fetch(SyncTask.class, taskName)
            .flatMap(task -> {
                SyncTask.SyncTaskStatus status = task.getStatus();
                status.setPhase(SyncTask.SyncPhase.FAILED);
                status.setMessage("Reconcile error: " + error.getMessage());
                status.setRetryCount(status.getRetryCount() + 1);
                status.setCompletionTime(Instant.now().toString());
                
                return client.update(task);
            })
            .onErrorResume(e -> {
                log.error("Failed to update task error status", e);
                return Mono.empty();
            })
            .then();
    }
}
