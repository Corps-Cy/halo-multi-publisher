package run.halo.sync.publisher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.controller.Reconciler;
import run.halo.sync.publisher.extension.SyncPlatform;
import run.halo.sync.publisher.extension.SyncTask;
import run.halo.sync.publisher.reconciler.SyncTaskReconciler;

import java.util.Optional;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SyncTaskReconciler 单元测试
 */
@ExtendWith(MockitoExtension.class)
class SyncTaskReconcilerTest {

    @Mock
    private ExtensionClient client;

    private java.util.List<run.halo.sync.publisher.adapter.PlatformAdapter> adapters = Collections.emptyList();

    @Test
    void testReconcile_WhenTaskNotFound_ShouldReturnDoNotRetry() {
        // Arrange
        SyncTaskReconciler reconciler = new SyncTaskReconciler(client, adapters);
        Reconciler.Request request = new Reconciler.Request("non-existent-task");
        
        when(client.fetch(SyncTask.class, "non-existent-task")).thenReturn(Optional.empty());

        // Act
        Reconciler.Result result = reconciler.reconcile(request);

        // Assert
        assertNotNull(result);
        assertFalse(result.reEnqueue());
    }

    @Test
    void testReconcile_WhenTaskAlreadySuccess_ShouldReturnDoNotRetry() {
        // Arrange
        SyncTaskReconciler reconciler = new SyncTaskReconciler(client, adapters);
        Reconciler.Request request = new Reconciler.Request("completed-task");
        
        SyncTask task = new SyncTask();
        task.setMetadata(new run.halo.app.extension.Metadata());
        task.getMetadata().setName("completed-task");
        task.setSpec(new SyncTask.Spec());
        task.setStatus(new SyncTask.Status());
        task.getStatus().setPhase(SyncTask.PHASE_SUCCESS);
        
        when(client.fetch(SyncTask.class, "completed-task")).thenReturn(Optional.of(task));

        // Act
        Reconciler.Result result = reconciler.reconcile(request);

        // Assert
        assertNotNull(result);
        assertFalse(result.reEnqueue());
    }

    @Test
    void testReconcile_WhenTaskAlreadyFailed_ShouldReturnDoNotRetry() {
        // Arrange
        SyncTaskReconciler reconciler = new SyncTaskReconciler(client, adapters);
        Reconciler.Request request = new Reconciler.Request("failed-task");
        
        SyncTask task = new SyncTask();
        task.setMetadata(new run.halo.app.extension.Metadata());
        task.getMetadata().setName("failed-task");
        task.setSpec(new SyncTask.Spec());
        task.setStatus(new SyncTask.Status());
        task.getStatus().setPhase(SyncTask.PHASE_FAILED);
        task.getStatus().setRetryCount(3);
        
        when(client.fetch(SyncTask.class, "failed-task")).thenReturn(Optional.of(task));

        // Act
        Reconciler.Result result = reconciler.reconcile(request);

        // Assert
        assertNotNull(result);
        assertFalse(result.reEnqueue());
    }
}
