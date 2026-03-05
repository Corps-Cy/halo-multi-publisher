<template>
  <v-page header-title="多平台同步" header-hero-icon="ri:share-circle-line">
    <template #header-actions>
      <v-button type="secondary" @click="$router.push('/sync-platforms/create')">
        <template #icon>
          <Icon icon="ri:add-line" />
        </template>
        添加平台
      </v-button>
    </template>

    <!-- 平台列表 -->
    <v-card title="已配置平台" class="mb-4">
      <v-table :columns="platformColumns" :data-source="platforms" :loading="loading" row-key="metadata.name">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'platformType'">
            <div class="flex items-center gap-2">
              <Icon :icon="getPlatformIcon(record.spec.platformType)" />
              <span>{{ getPlatformName(record.spec.platformType) }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'status'">
            <v-tag :color="record.status?.connected ? 'green' : 'red'">
              {{ record.status?.connected ? '已连接' : '未连接' }}
            </v-tag>
          </template>
          <template v-else-if="column.key === 'autoSync'">
            <v-switch 
              :model-value="record.spec.rules?.autoSync" 
              @update:model-value="toggleAutoSync(record, $event)" 
            />
          </template>
          <template v-else-if="column.key === 'enabled'">
            <v-switch v-model="record.spec.enabled" @change="toggleEnabled(record)" />
          </template>
          <template v-else-if="column.key === 'actions'">
            <v-space>
              <v-button size="small" type="link" @click="testConnection(record)">
                测试连接
              </v-button>
              <v-button size="small" type="link" @click="editPlatform(record)">
                编辑
              </v-button>
              <v-button size="small" type="link" danger @click="confirmDelete(record)">
                删除
              </v-button>
            </v-space>
          </template>
        </template>
      </v-table>
    </v-card>

    <!-- 同步任务列表 -->
    <v-card title="同步任务">
      <template #actions>
        <v-button type="secondary" size="small" @click="fetchTasks">
          <template #icon><Icon icon="ri:refresh-line" /></template>
          刷新
        </v-button>
      </template>
      <v-table :columns="taskColumns" :data-source="tasks" :loading="tasksLoading" row-key="metadata.name">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'postName'">
            <a @click="viewPost(record.spec.postName)">{{ record.spec.postName }}</a>
          </template>
          <template v-else-if="column.key === 'phase'">
            <v-tag :color="getPhaseColor(record.status?.phase)">
              {{ getPhaseText(record.status?.phase) }}
            </v-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <v-space>
              <v-button 
                v-if="record.status?.externalUrl" 
                size="small" 
                type="link"
                @click="openExternal(record.status?.externalUrl)"
              >
                查看文章
              </v-button>
              <v-button 
                v-if="record.status?.phase === 'failed'" 
                size="small" 
                type="link"
                @click="retryTask(record)"
              >
                重试
              </v-button>
              <v-button 
                v-if="record.status?.phase === 'pending'" 
                size="small" 
                type="link"
                danger
                @click="cancelTask(record)"
              >
                取消
              </v-button>
            </v-space>
          </template>
        </template>
      </v-table>
    </v-card>
  </v-page>
</template>

<script lang="ts" setup>
import { ref, onMounted } from "vue";
import { apiClient } from "@halo-dev/console-shared";
import { useToast } from "@halo-dev/console-shared";
import { useRouter } from "vue-router";

const toast = useToast();
const router = useRouter();

const loading = ref(false);
const tasksLoading = ref(false);
const platforms = ref<any[]>([]);
const tasks = ref<any[]>([]);

const platformColumns = [
  { title: "平台", key: "platformType", width: 150 },
  { title: "名称", dataIndex: "spec.displayName", width: 150 },
  { title: "状态", key: "status", width: 100 },
  { title: "自动同步", key: "autoSync", width: 100 },
  { title: "启用", key: "enabled", width: 80 },
  { title: "已同步", dataIndex: "status.totalSynced", width: 80 },
  { title: "最后同步", dataIndex: "status.lastSyncTime", width: 150 },
  { title: "操作", key: "actions", width: 200 },
];

const taskColumns = [
  { title: "文章", key: "postName", width: 200 },
  { title: "目标平台", dataIndex: "spec.platformName", width: 120 },
  { title: "动作", dataIndex: "spec.action", width: 80 },
  { title: "状态", key: "phase", width: 100 },
  { title: "消息", dataIndex: "status.message", ellipsis: true },
  { title: "创建时间", dataIndex: "metadata.creationTimestamp", width: 150 },
  { title: "操作", key: "actions", width: 150 },
];

const platformIcons: Record<string, string> = {
  wechat: "ri:wechat-fill",
  juejin: "simple-icons:juejin",
  zhihu: "ri:zhihu-fill",
  toutiao: "ri:article-line",
};

const platformNames: Record<string, string> = {
  wechat: "微信公众号",
  juejin: "掘金",
  zhihu: "知乎",
  toutiao: "今日头条",
};

const getPlatformIcon = (type: string) => platformIcons[type] || "ri:share-line";
const getPlatformName = (type: string) => platformNames[type] || type;

const phaseColors: Record<string, string> = {
  pending: "default",
  running: "blue",
  success: "green",
  failed: "red",
  cancelled: "gray",
};

const phaseTexts: Record<string, string> = {
  pending: "等待中",
  running: "执行中",
  success: "成功",
  failed: "失败",
  cancelled: "已取消",
};

const getPhaseColor = (phase: string) => phaseColors[phase] || "default";
const getPhaseText = (phase: string) => phaseTexts[phase] || phase;

const fetchPlatforms = async () => {
  loading.value = true;
  try {
    const { data } = await apiClient.extension.customResource.list(
      "sync.halo.run",
      "v1alpha1",
      "syncplatforms"
    );
    platforms.value = data.items || [];
  } catch (e) {
    console.error("Failed to fetch platforms", e);
    toast.error("加载平台列表失败");
  } finally {
    loading.value = false;
  }
};

const fetchTasks = async () => {
  tasksLoading.value = true;
  try {
    const { data } = await apiClient.extension.customResource.list(
      "sync.halo.run",
      "v1alpha1",
      "synctasks"
    );
    // 按创建时间倒序
    tasks.value = (data.items || []).sort((a: any, b: any) => 
      new Date(b.metadata.creationTimestamp).getTime() - 
      new Date(a.metadata.creationTimestamp).getTime()
    );
  } catch (e) {
    console.error("Failed to fetch tasks", e);
    toast.error("加载任务列表失败");
  } finally {
    tasksLoading.value = false;
  }
};

const toggleEnabled = async (platform: any) => {
  try {
    await apiClient.extension.customResource.update(
      "sync.halo.run",
      "v1alpha1",
      "syncplatforms",
      platform.metadata.name,
      platform
    );
    toast.success("状态已更新");
  } catch (e) {
    console.error("Failed to update platform", e);
    toast.error("更新失败");
    fetchPlatforms();
  }
};

const toggleAutoSync = async (platform: any, value: boolean) => {
  if (!platform.spec.rules) {
    platform.spec.rules = {};
  }
  platform.spec.rules.autoSync = value;
  
  try {
    await apiClient.extension.customResource.update(
      "sync.halo.run",
      "v1alpha1",
      "syncplatforms",
      platform.metadata.name,
      platform
    );
    toast.success(value ? "已开启自动同步" : "已关闭自动同步");
  } catch (e) {
    console.error("Failed to update auto sync", e);
    toast.error("更新失败");
    fetchPlatforms();
  }
};

const testConnection = async (platform: any) => {
  toast.info("正在测试连接...");
  try {
    const response = await fetch(
      `/apis/sync.halo.run/v1alpha1/syncplatforms/${platform.metadata.name}/test`,
      { method: "POST" }
    );
    const result = await response.json();
    
    if (result.connected) {
      toast.success("连接成功");
    } else {
      toast.error("连接失败: " + (result.message || "未知错误"));
    }
    
    fetchPlatforms();
  } catch (e) {
    console.error("Connection test failed", e);
    toast.error("测试失败");
  }
};

const editPlatform = (platform: any) => {
  router.push(`/sync-platforms/${platform.metadata.name}/edit`);
};

const confirmDelete = async (platform: any) => {
  if (!confirm(`确定要删除平台 "${platform.spec.displayName}" 吗？`)) {
    return;
  }
  
  try {
    await apiClient.extension.customResource.delete(
      "sync.halo.run",
      "v1alpha1",
      "syncplatforms",
      platform.metadata.name
    );
    toast.success("删除成功");
    fetchPlatforms();
  } catch (e) {
    console.error("Failed to delete platform", e);
    toast.error("删除失败");
  }
};

const retryTask = async (task: any) => {
  task.status.phase = "pending";
  task.status.retryCount = (task.status.retryCount || 0) + 1;
  task.status.message = "等待重试...";
  
  try {
    await apiClient.extension.customResource.update(
      "sync.halo.run",
      "v1alpha1",
      "synctasks",
      task.metadata.name,
      task
    );
    toast.success("已重新加入队列");
    fetchTasks();
  } catch (e) {
    console.error("Failed to retry task", e);
    toast.error("重试失败");
  }
};

const cancelTask = async (task: any) => {
  task.status.phase = "cancelled";
  task.status.message = "用户取消";
  
  try {
    await apiClient.extension.customResource.update(
      "sync.halo.run",
      "v1alpha1",
      "synctasks",
      task.metadata.name,
      task
    );
    toast.success("任务已取消");
    fetchTasks();
  } catch (e) {
    console.error("Failed to cancel task", e);
    toast.error("取消失败");
  }
};

const viewPost = (postName: string) => {
  router.push(`/posts/editor?name=${postName}`);
};

const openExternal = (url: string) => {
  window.open(url, "_blank");
};

onMounted(() => {
  fetchPlatforms();
  fetchTasks();
});
</script>

<style scoped>
.flex {
  display: flex;
}
.items-center {
  align-items: center;
}
.gap-2 {
  gap: 0.5rem;
}
</style>
