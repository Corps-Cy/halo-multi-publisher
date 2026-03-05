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
      <v-table :columns="platformColumns" :data-source="platforms" :loading="loading">
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
              <v-button size="small" type="link" danger @click="deletePlatform(record)">
                删除
              </v-button>
            </v-space>
          </template>
        </template>
      </v-table>
    </v-card>

    <!-- 同步任务列表 -->
    <v-card title="同步任务">
      <v-table :columns="taskColumns" :data-source="tasks" :loading="tasksLoading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'phase'">
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
  { title: "平台", key: "platformType" },
  { title: "名称", dataIndex: "spec.displayName" },
  { title: "状态", key: "status" },
  { title: "自动同步", dataIndex: "spec.rules.autoSync" },
  { title: "启用", key: "enabled" },
  { title: "操作", key: "actions" },
];

const taskColumns = [
  { title: "文章", dataIndex: "spec.postName" },
  { title: "目标平台", dataIndex: "spec.platformName" },
  { title: "动作", dataIndex: "spec.action" },
  { title: "状态", key: "phase" },
  { title: "消息", dataIndex: "status.message" },
  { title: "创建时间", dataIndex: "metadata.creationTimestamp" },
  { title: "操作", key: "actions" },
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
    tasks.value = data.items || [];
  } catch (e) {
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
    toast.error("更新失败");
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
      toast.error("连接失败: " + result.message);
    }
    
    fetchPlatforms();
  } catch (e) {
    toast.error("测试失败");
  }
};

const editPlatform = (platform: any) => {
  router.push(`/sync-platforms/${platform.metadata.name}/edit`);
};

const deletePlatform = async (platform: any) => {
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
    toast.error("删除失败");
  }
};

const retryTask = async (task: any) => {
  task.status.phase = "pending";
  task.status.retryCount = (task.status.retryCount || 0) + 1;
  
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
    toast.error("重试失败");
  }
};

const openExternal = (url: string) => {
  window.open(url, "_blank");
};

onMounted(() => {
  fetchPlatforms();
  fetchTasks();
});
</script>
