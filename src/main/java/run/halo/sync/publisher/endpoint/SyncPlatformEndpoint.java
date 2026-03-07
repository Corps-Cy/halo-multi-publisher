package run.halo.sync.publisher.endpoint;

import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.sync.publisher.adapter.PlatformAdapter;
import run.halo.sync.publisher.extension.SyncPlatform;
import run.halo.sync.publisher.extension.SyncTask;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SyncPlatformEndpoint implements CustomEndpoint {

    private static final String GROUP = "sync.halo.run";
    private static final String VERSION = "v1alpha1";

    private final ReactiveExtensionClient client;
    private final List<PlatformAdapter> adapters;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = GROUP + "/" + VERSION + "/SyncPlatform";
        return SpringdocRouteBuilder.route()
            // 获取所有平台
            .GET("/platforms", this::listPlatforms,
                builder -> builder.operationId("listPlatforms")
                    .description("获取所有同步平台配置")
                    .tag(tag))
            // 获取平台详情
            .GET("/platforms/{name}", this::getPlatform,
                builder -> builder.operationId("getPlatform")
                    .description("获取指定平台配置")
                    .tag(tag))
            // 验证平台凭证
            .POST("/platforms/{name}/validate", this::validatePlatform,
                builder -> builder.operationId("validatePlatform")
                    .description("验证平台凭证是否有效")
                    .tag(tag))
            // 获取同步任务列表
            .GET("/tasks", this::listTasks,
                builder -> builder.operationId("listTasks")
                    .description("获取同步任务列表")
                    .tag(tag))
            // 手动触发同步
            .POST("/tasks", this::createTask,
                builder -> builder.operationId("createTask")
                    .description("手动创建同步任务")
                    .tag(tag))
            // 获取任务详情
            .GET("/tasks/{name}", this::getTask,
                builder -> builder.operationId("getTask")
                    .description("获取同步任务详情")
                    .tag(tag))
            .build();
    }

    private Mono<ServerResponse> listPlatforms(ServerRequest request) {
        return client.listAll(SyncPlatform.class, null, null)
            .collectList()
            .flatMap(platforms -> ServerResponse.ok().bodyValue(platforms));
    }

    private Mono<ServerResponse> getPlatform(ServerRequest request) {
        String name = request.pathVariable("name");
        return client.fetch(SyncPlatform.class, name)
            .flatMap(platform -> ServerResponse.ok().bodyValue(platform))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> validatePlatform(ServerRequest request) {
        String name = request.pathVariable("name");
        return client.fetch(SyncPlatform.class, name)
            .flatMap(platform -> {
                PlatformAdapter adapter = getAdapter(platform.getSpec().getPlatformType());
                if (adapter == null) {
                    return ServerResponse.badRequest()
                        .bodyValue(Map.of("error", "不支持的平台类型"));
                }
                return adapter.validateCredentials(platform)
                    .flatMap(valid -> ServerResponse.ok()
                        .bodyValue(Map.of("valid", valid)));
            })
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> listTasks(ServerRequest request) {
        String platformName = request.queryParam("platform").orElse(null);

        return client.listAll(SyncTask.class, null, null)
            .filter(task -> platformName == null || platformName.equals(task.getSpec().getPlatformName()))
            .collectList()
            .flatMap(tasks -> ServerResponse.ok().bodyValue(tasks));
    }

    private Mono<ServerResponse> createTask(ServerRequest request) {
        return request.bodyToMono(SyncTask.class)
            .flatMap(task -> {
                if (task.getStatus() == null) {
                    task.setStatus(new SyncTask.Status());
                    task.getStatus().setPhase(SyncTask.PHASE_PENDING);
                }
                return client.create(task);
            })
            .flatMap(created -> ServerResponse.ok().bodyValue(created))
            .onErrorResume(e -> ServerResponse.badRequest()
                .bodyValue(Map.of("error", e.getMessage())));
    }

    private Mono<ServerResponse> getTask(ServerRequest request) {
        String name = request.pathVariable("name");
        return client.fetch(SyncTask.class, name)
            .flatMap(task -> ServerResponse.ok().bodyValue(task))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private PlatformAdapter getAdapter(SyncPlatform.PlatformType type) {
        return adapters.stream()
            .filter(a -> a.getPlatformType() == type)
            .findFirst()
            .orElse(null);
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion(GROUP, VERSION);
    }
}
