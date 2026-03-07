<script setup lang="ts">
import { ref, onMounted } from "vue";
import { apiClient } from "@halo-dev/console-shared";
import { useToast, useDialog } from "@halo-dev/console-shared";

const toast = useToast();
const dialog = useDialog();
const tasks = ref<any[]>([]);
const loading = ref(false);
const platformFilter = ref("");

const columns = [
  { title: "任务名称", dataIndex: "metadata.name", key: "name" },
  { title: "文章", dataIndex: "spec.postName", key: "post" },
  { title: "目标平台", dataIndex: "spec.platformName", key: "platform" },
  { title: "操作类型", dataIndex: "spec.action", key: "action" },
  { title: "状态", dataIndex: "status.phase", key: "phase" },
  { title: "重试", dataIndex: "status.retryCount", key: "retry" },
  { title: "创建时间", dataIndex: "metadata.creationTimestamp", key: "created" },
  { title: "操作", key: "actions", width: 180 }
];

const fetchTasks = async () => {
  loading.value = true;
  try {
    const { data } = await apiClient.extension.customResource.list(
      "sync.halo.run",
      "v1alpha1",
      "synctasks"
    );
    let items = data.items || [];
    
    if (platformFilter.value) {
      items = items.filter(
        (t: any) => t.spec.platformName === platformFilter.value
      );
    }
    
    tasks.value = items;
  } catch (e: any) {
    toast.error("加载任务列表失败: " + (e.message || "未知错误"));
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const getActionLabel = (action: string) => {
  switch (action) {
    case "CREATE":
      return "创建";
    case "UPDATE":
      return "更新";
    case "DELETE":
      return "删除";
    default:
      return action;
  }
};

const getActionTag = (action: string) => {
  switch (action) {
    case "CREATE":
      return "success";
    case "UPDATE":
      return "info";
    case "DELETE":
      return "error";
    default:
      return "default";
  }
};

const getPhaseLabel = (phase: string) => {
  switch (phase) {
    case "Pending":
      return "等待中";
    case "Running":
      return "执行中";
    case "Success":
      return "成功";
    case "Failed":
      return "失败";
    default:
      return phase || "等待中";
  }
};

const getPhaseTag = (phase: string) => {
  switch (phase) {
    case "Success":
      return "success";
    case "Failed":
      return "error";
    case "Running":
      return "info";
    default:
      return "warning";
  }
};

const formatDate = (dateStr: string) => {
  if (!dateStr) return "-";
  const date = new Date(dateStr);
  return date.toLocaleString("zh-CN");
};

const handleViewExternal = (record: any) => {
  const url = record.status?.externalUrl;
  if (url) {
    window.open(url, "_blank");
  } else {
    toast.info("暂无外部链接");
  }
};

const handleRetry = async (record: any) => {
  dialog.warning({
    title: "重试任务",
    content: "确定要重新执行此同步任务吗？",
    onConfirm: async () => {
      try {
        // 重置状态为 Pending
        record.status.phase = "Pending";
        record.status.errorMessage = null;
        
        await apiClient.extension.customResource.update(
          "sync.halo.run",
          "v1alpha1",
          "synctasks",
          record.metadata.name,
          record
        );
        toast.success("已触发重试");
        fetchTasks();
      } catch (e: any) {
        toast.error("重试失败: " + (e.message || "未知错误"));
      }
    }
  });
};

const handleDelete = (record: any) => {
  dialog.warning({
    title: "删除任务",
    content: "确定要删除此同步任务记录吗？",
    onConfirm: async () => {
      try {
        await apiClient.extension.customResource.delete(
          "sync.halo.run",
          "v1alpha1",
          "synctasks",
          record.metadata.name
        );
        toast.success("删除成功");
        fetchTasks();
      } catch (e: any) {
        toast.error("删除失败: " + (e.message || "未知错误"));
      }
    }
  });
};

const handleViewError = (record: any) => {
  const error = record.status?.errorMessage || "无错误信息";
  dialog.error({
    title: "错误详情",
    content: error
  });
};

const clearFilter = () => {
  platformFilter.value = "";
  fetchTasks();
};

onMounted(fetchTasks);
</script>

<template>
  <v-card title="同步任务" :loading="loading">
    <template #extra>
      <v-space>
        <v-input
          v-model:value="platformFilter"
          placeholder="按平台筛选"
          style="width: 200px;"
          @change="fetchTasks"
        />
        <v-button v-if="platformFilter" type="link" @click="clearFilter">
          清除筛选
        </v-button>
        <v-button type="secondary" @click="fetchTasks">
          <template #icon>
            <Icon icon="ri:refresh-line" />
          </template>
          刷新
        </v-button>
      </v-space>
    </template>

    <v-table
      :columns="columns"
      :data-source="tasks"
      row-key="metadata.name"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <v-tag :type="getActionTag(record.spec.action)">
            {{ getActionLabel(record.spec.action) }}
          </v-tag>
        </template>
        <template v-else-if="column.key === 'phase'">
          <v-tag :type="getPhaseTag(record.status?.phase)">
            {{ getPhaseLabel(record.status?.phase) }}
          </v-tag>
        </template>
        <template v-else-if="column.key === 'retry'">
          <span :class="{ 'text-danger': record.status?.retryCount >= 3 }">
            {{ record.status?.retryCount || 0 }}/3
          </span>
        </template>
        <template v-else-if="column.key === 'created'">
          {{ formatDate(record.metadata.creationTimestamp) }}
        </template>
        <template v-else-if="column.key === 'actions'">
          <v-space>
            <v-button
              v-if="record.status?.externalUrl"
              size="small"
              type="link"
              @click="handleViewExternal(record)"
            >
              查看
            </v-button>
            <v-button
              v-if="record.status?.phase === 'Failed'"
              size="small"
              type="link"
              @click="handleViewError(record)"
            >
              错误
            </v-button>
            <v-button
              v-if="record.status?.phase === 'Failed' && (record.status?.retryCount || 0) < 3"
              size="small"
              type="link"
              @click="handleRetry(record)"
            >
              重试
            </v-button>
            <v-button
              size="small"
              type="link"
              danger
              @click="handleDelete(record)"
            >
              删除
            </v-button>
          </v-space>
        </template>
      </template>
    </v-table>

    <div v-if="tasks.length === 0 && !loading" class="empty-state">
      <Icon icon="ri:task-line" style="font-size: 48px; color: #ccc;" />
      <p>暂无同步任务</p>
      <p class="hint">启用平台的自动同步后，发布文章时会自动创建同步任务</p>
    </div>
  </v-card>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px;
  color: #999;
}

.empty-state p {
  margin: 8px 0;
}

.empty-state .hint {
  font-size: 12px;
  color: #bbb;
}

.text-danger {
  color: #f5222d;
}
</style>
