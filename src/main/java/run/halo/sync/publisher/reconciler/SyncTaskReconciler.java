package run.halo.sync.publisher.reconciler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;
import run.halo.sync.publisher.adapter.PlatformAdapter;
import run.halo.sync.publisher.adapter.PlatformAdapter.PublishResult;
import run.halo.sync.publisher.extension.SyncPlatform;
import run.halo.sync.publisher.extension.SyncTask;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncTaskReconciler implements Reconciler<Reconciler.Request> {

    private final ExtensionClient client;
    private final List<PlatformAdapter> adapters;

    private Map<SyncPlatform.PlatformType, PlatformAdapter> adapterMap;

    @Override
    public Result reconcile(Request request) {
        Optional<SyncTask> taskOpt = client.fetch(SyncTask.class, request.name());
        if (taskOpt.isEmpty()) {
            log.warn("SyncTask {} not found", request.name());
            return Result.doNotRetry();
        }

        SyncTask task = taskOpt.get();
        String phase = task.getStatus() != null ? task.getStatus().getPhase() : null;

        // 已完成或已失败的任务不再处理
        if (SyncTask.PHASE_SUCCESS.equals(phase) || SyncTask.PHASE_FAILED.equals(phase)) {
            return Result.doNotRetry();
        }

        try {
            // 初始化状态
            if (task.getStatus() == null) {
                task.setStatus(new SyncTask.Status());
            }

            PublishResult result = executeSync(task);
            if (result != null) {
                updateTaskStatus(task, result);
            }
            return Result.doNotRetry();

        } catch (Exception e) {
            log.error("Sync failed for task {}: {}", task.getMetadata().getName(), e.getMessage());
            updateTaskFailed(task, e.getMessage());

            int retryCount = task.getStatus().getRetryCount() != null ? task.getStatus().getRetryCount() : 0;
            if (retryCount >= 3) {
                return Result.doNotRetry();
            }
            return Result.requeue(Duration.ofMinutes(5));
        }
    }

    private PublishResult executeSync(SyncTask task) {
        String platformName = task.getSpec().getPlatformName();

        Optional<SyncPlatform> platformOpt = client.fetch(SyncPlatform.class, platformName);
        if (platformOpt.isEmpty()) {
            throw new RuntimeException("平台配置不存在: " + platformName);
        }

        SyncPlatform platform = platformOpt.get();

        // 检查平台是否启用
        if (!Boolean.TRUE.equals(platform.getSpec().getEnabled())) {
            throw new RuntimeException("平台未启用: " + platformName);
        }

        // 获取对应的适配器
        PlatformAdapter adapter = getAdapter(platform.getSpec().getPlatformType());
        if (adapter == null) {
            throw new RuntimeException("不支持的平台类型: " + platform.getSpec().getPlatformType());
        }

        // 更新任务状态为 Running
        task.getStatus().setPhase(SyncTask.PHASE_RUNNING);
        task.getStatus().setStartTime(Instant.now().toString());
        client.update(task);

        return executeAction(adapter, task, platform);
    }

    private PublishResult executeAction(PlatformAdapter adapter, SyncTask task, SyncPlatform platform) {
        String postName = task.getSpec().getPostName();
        SyncTask.ActionType action = task.getSpec().getAction();

        // TODO: 从 Post Extension 获取文章内容和标题
        String content = "# 示例内容\n\n这是文章内容...";
        String title = "示例标题";

        return switch (action) {
            case CREATE -> adapter.publish(content, title, platform).block();
            case UPDATE -> {
                String externalId = task.getStatus() != null ? task.getStatus().getExternalId() : null;
                if (externalId == null) {
                    throw new RuntimeException("缺少外部文章 ID");
                }
                yield adapter.update(externalId, content, title, platform).block();
            }
            case DELETE -> {
                String externalId = task.getStatus() != null ? task.getStatus().getExternalId() : null;
                if (externalId == null) {
                    yield new PublishResult(null, null, "无需删除");
                }
                adapter.delete(externalId, platform).block();
                yield new PublishResult(null, null, "删除成功");
            }
        };
    }

    private void updateTaskStatus(SyncTask task, PublishResult result) {
        SyncTask.Status status = task.getStatus();

        status.setPhase(SyncTask.PHASE_SUCCESS);
        status.setExternalId(result.externalId());
        status.setExternalUrl(result.externalUrl());
        status.setCompletionTime(Instant.now().toString());
        status.setErrorMessage(null);

        client.update(task);
    }

    private void updateTaskFailed(SyncTask task, String errorMessage) {
        SyncTask.Status status = task.getStatus();

        status.setPhase(SyncTask.PHASE_FAILED);
        status.setErrorMessage(errorMessage);
        status.setCompletionTime(Instant.now().toString());
        status.setRetryCount(status.getRetryCount() + 1);

        client.update(task);
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
            .extension(new SyncTask())
            .build();
    }
}
