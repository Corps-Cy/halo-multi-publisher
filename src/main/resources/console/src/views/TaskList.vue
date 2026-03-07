<script setup lang="ts">
import { ref, onMounted } from "vue";
import { apiClient } from "@halo-dev/console-shared";
import { useToast } from "@halo-dev/console-shared";

const toast = useToast();
const tasks = ref([]);
const loading = ref(false);

const columns = [
  { title: "任务名称", dataIndex: "metadata.name", key: "name" },
  { title: "文章", dataIndex: "spec.postName", key: "post" },
  { title: "目标平台", dataIndex: "spec.platformName", key: "platform" },
  { title: "操作", dataIndex: "spec.action", key: "action" },
  { title: "状态", dataIndex: "status.phase", key: "phase" },
  { title: "创建时间", dataIndex: "metadata.creationTimestamp", key: "created" },
  { title: "操作", key: "actions" }
];

const fetchTasks = async () => {
  loading.value = true;
  try {
    const { data } = await apiClient.extension.customResource.list(
      "sync.halo.run",
      "v1alpha1",
      "synctasks"
    );
    tasks.value = data.items || [];
  } catch (e) {
    toast.error("加载任务列表失败");
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const getActionLabel = (action: string) => {
  switch (action) {
    case "CREATE": return "创建";
    case "UPDATE": return "更新";
    case "DELETE": return "删除";
    default: return action;
  }
};

const getPhaseType = (phase: string) => {
  switch (phase) {
    case "Success": return "success";
    case "Failed": return "error";
    case "Running": return "info";
    default: return "warning";
  }
};

const handleViewExternal = (record: any) => {
  const url = record.status?.externalUrl;
  if (url) {
    window.open(url, "_blank");
  }
};

const handleRetry = async (record: any) => {
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
  } catch (e) {
    toast.error("重试失败");
  }
};

const handleDelete = async (record: any) => {
  try {
    await apiClient.extension.customResource.delete(
      "sync.halo.run",
      "v1alpha1",
      "synctasks",
      record.metadata.name
    );
    toast.success("删除成功");
    fetchTasks();
  } catch (e) {
    toast.error("删除失败");
  }
};

onMounted(fetchTasks);
</script>

<template>
  <v-card title="同步任务" :loading="loading">
    <template #extra>
      <v-button type="secondary" @click="fetchTasks">
        <template #icon>
          <Icon icon="ri:refresh-line" />
        </template>
        刷新
      </v-button>
    </template>

    <v-table
      :columns="columns"
      :data-source="tasks"
      row-key="metadata.name"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <v-tag>{{ getActionLabel(record.spec.action) }}</v-tag>
        </template>
        <template v-else-if="column.key === 'phase'">
          <v-tag :type="getPhaseType(record.status?.phase)">
            {{ record.status?.phase || 'Pending' }}
          </v-tag>
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
              @click="handleRetry(record)"
            >
              重试
            </v-button>
            <v-button size="small" type="link" danger @click="handleDelete(record)">
              删除
            </v-button>
          </v-space>
        </template>
      </template>
    </v-table>
  </v-card>
</template>
