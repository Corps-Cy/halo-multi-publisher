package run.halo.sync.publisher.reconciler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;
import run.halo.sync.publisher.adapter.PlatformAdapter;
import run.halo.sync.publisher.adapter.PlatformAdapter.PublishResult;
import run.halo.sync.publisher.extension.SyncPlatform;
import run.halo.sync.publisher.extension.SyncTask;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncTaskReconciler implements Reconciler<Reconciler.Request> {

    private final ReactiveExtensionClient client;
    private final List<PlatformAdapter> adapters;

    private Map<SyncPlatform.PlatformType, PlatformAdapter> adapterMap;

    @Override
    public Mono<Result> reconcile(Request request) {
        return client.fetch(SyncTask.class, request.name())
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("SyncTask {} not found", request.name());
                return Mono.empty();
            }))
            .flatMap(this::reconcileTask)
            .onErrorResume(e -> {
                log.error("Failed to reconcile SyncTask {}: {}", request.name(), e.getMessage());
                return handleReconcileError(request.name(), e);
            });
    }

    private Mono<Result> reconcileTask(SyncTask task) {
        String phase = task.getStatus() != null ? task.getStatus().getPhase() : null;

        // 已完成或已失败的任务不再处理
        if (SyncTask.PHASE_SUCCESS.equals(phase) || SyncTask.PHASE_FAILED.equals(phase)) {
            return Mono.just(Result.doNotRetry());
        }

        // 初始化状态
        if (task.getStatus() == null) {
            task.setStatus(new SyncTask.Status());
        }

        return executeSync(task)
            .flatMap(result -> updateTaskStatus(task, result))
            .thenReturn(Result.doNotRetry())
            .onErrorResume(e -> {
                log.error("Sync failed for task {}: {}", task.getMetadata().getName(), e.getMessage());
                return updateTaskFailed(task, e.getMessage())
                    .thenReturn(Result.requeue(Duration.ofMinutes(5))); // 5 分钟后重试
            });
    }

    private Mono<PublishResult> executeSync(SyncTask task) {
        String platformName = task.getSpec().getPlatformName();

        return client.fetch(SyncPlatform.class, platformName)
            .switchIfEmpty(Mono.error(new RuntimeException("平台配置不存在: " + platformName)))
            .flatMap(platform -> {
                // 检查平台是否启用
                if (!Boolean.TRUE.equals(platform.getSpec().getEnabled())) {
                    return Mono.error(new RuntimeException("平台未启用: " + platformName));
                }

                // 获取对应的适配器
                PlatformAdapter adapter = getAdapter(platform.getSpec().getPlatformType());
                if (adapter == null) {
                    return Mono.error(new RuntimeException("不支持的平台类型: " + platform.getSpec().getPlatformType()));
                }

                // 更新任务状态为 Running
                task.getStatus().setPhase(SyncTask.PHASE_RUNNING);
                task.getStatus().setStartTime(Instant.now().toString());

                return client.update(task)
                    .then(executeAction(adapter, task, platform));
            });
    }

    private Mono<PublishResult> executeAction(PlatformAdapter adapter, SyncTask task, SyncPlatform platform) {
        String postName = task.getSpec().getPostName();
        SyncTask.ActionType action = task.getSpec().getAction();

        // TODO: 从 Post Extension 获取文章内容和标题
        // 这里简化处理，实际需要通过 client.fetch(Post.class, postName) 获取
        String content = "# 示例内容\n\n这是文章内容...";
        String title = "示例标题";

        return switch (action) {
            case CREATE -> adapter.publish(content, title, platform);
            case UPDATE -> {
                String externalId = task.getStatus() != null ? task.getStatus().getExternalId() : null;
                if (externalId == null) {
                    yield Mono.error(new RuntimeException("缺少外部文章 ID"));
                }
                yield adapter.update(externalId, content, title, platform);
            }
            case DELETE -> {
                String externalId = task.getStatus() != null ? task.getStatus().getExternalId() : null;
                if (externalId == null) {
                    yield Mono.empty(); // 无需删除
                }
                yield adapter.delete(externalId, platform)
                    .then(Mono.just(new PublishResult(null, null, "删除成功")));
            }
        };
    }

    private Mono<Void> updateTaskStatus(SyncTask task, PublishResult result) {
        SyncTask.Status status = task.getStatus();
        String oldPhase = status.getPhase();

        status.setPhase(SyncTask.PHASE_SUCCESS);
        status.setExternalId(result.externalId());
        status.setExternalUrl(result.externalUrl());
        status.setCompletionTime(Instant.now().toString());
        status.setErrorMessage(null);

        // 只有状态变化时才更新，避免无限循环
        if (!SyncTask.PHASE_SUCCESS.equals(oldPhase)) {
            return client.update(task).then();
        }
        return Mono.empty();
    }

    private Mono<Void> updateTaskFailed(SyncTask task, String errorMessage) {
        SyncTask.Status status = task.getStatus();
        String oldPhase = status.getPhase();

        status.setPhase(SyncTask.PHASE_FAILED);
        status.setErrorMessage(errorMessage);
        status.setCompletionTime(Instant.now().toString());
        status.setRetryCount(status.getRetryCount() + 1);

        if (!SyncTask.PHASE_FAILED.equals(oldPhase)) {
            return client.update(task).then();
        }
        return Mono.empty();
    }

    private Mono<Result> handleReconcileError(String taskName, Throwable e) {
        return client.fetch(SyncTask.class, taskName)
            .flatMap(task -> {
                if (task.getStatus() == null) {
                    task.setStatus(new SyncTask.Status());
                }

                int retryCount = task.getStatus().getRetryCount() != null ? task.getStatus().getRetryCount() : 0;

                // 最多重试 3 次
                if (retryCount >= 3) {
                    return updateTaskFailed(task, e.getMessage())
                        .thenReturn(Result.doNotRetry());
                }

                return Mono.just(Result.requeue(Duration.ofMinutes(5)));
            })
            .switchIfEmpty(Mono.just(Result.doNotRetry()));
    }

    private PlatformAdapter getAdapter(SyncPlatform.PlatformType type) {
        if (adapterMap == null) {
            adapterMap = adapters.stream()
                .collect(Collectors.toMap(PlatformAdapter::getPlatformType, Function.identity()));
        }
        return adapterMap.get(type);
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder
            .extension(SyncTask.class)
            .build();
    }
}
