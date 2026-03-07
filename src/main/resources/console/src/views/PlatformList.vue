<script setup lang="ts">
import { ref, onMounted } from "vue";
import { apiClient } from "@halo-dev/console-shared";
import { useToast, useDialog } from "@halo-dev/console-shared";
import { useRouter } from "vue-router";

const router = useRouter();
const toast = useToast();
const dialog = useDialog();
const platforms = ref<any[]>([]);
const loading = ref(false);

const columns = [
  { title: "名称", dataIndex: "spec.displayName", key: "name" },
  { title: "平台类型", dataIndex: "spec.platformType", key: "type" },
  { title: "状态", key: "status" },
  { title: "自动同步", key: "autoSync" },
  { title: "操作", key: "actions", width: 200 }
];

const fetchPlatforms = async () => {
  loading.value = true;
  try {
    const { data } = await apiClient.extension.customResource.list(
      "sync.halo.run",
      "v1alpha1",
      "syncplatforms"
    );
    platforms.value = data.items || [];
  } catch (e: any) {
    toast.error("加载平台列表失败: " + (e.message || "未知错误"));
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const getPlatformTypeLabel = (type: string) => {
  switch (type) {
    case "WECHAT":
      return "微信公众号";
    case "JUEJIN":
      return "掘金";
    default:
      return type;
  }
};

const getPlatformTypeTag = (type: string) => {
  switch (type) {
    case "WECHAT":
      return "success";
    case "JUEJIN":
      return "info";
    default:
      return "default";
  }
};

const handleCreate = () => {
  router.push("/multi-publisher/platforms/create");
};

const handleEdit = (record: any) => {
  router.push(`/multi-publisher/platforms/${record.metadata.name}/edit`);
};

const handleValidate = async (record: any) => {
  try {
    const response = await fetch(
      `/apis/sync.halo.run/v1alpha1/platforms/${record.metadata.name}/validate`,
      { method: "POST" }
    );
    const result = await response.json();
    if (result.valid) {
      toast.success("凭证验证成功");
    } else {
      toast.warning("凭证验证失败，请检查配置");
    }
  } catch (e: any) {
    toast.error("验证请求失败: " + (e.message || "未知错误"));
  }
};

const handleToggle = async (record: any) => {
  const newEnabled = !record.spec.enabled;
  const action = newEnabled ? "启用" : "禁用";
  
  dialog.warning({
    title: `确认${action}`,
    content: `确定要${action}平台「${record.spec.displayName}」吗？`,
    onConfirm: async () => {
      try {
        record.spec.enabled = newEnabled;
        await apiClient.extension.customResource.update(
          "sync.halo.run",
          "v1alpha1",
          "syncplatforms",
          record.metadata.name,
          record
        );
        toast.success(`已${action}`);
        fetchPlatforms();
      } catch (e: any) {
        toast.error(`${action}失败: ` + (e.message || "未知错误"));
      }
    }
  });
};

const handleDelete = (record: any) => {
  dialog.warning({
    title: "删除确认",
    content: `确定要删除平台「${record.spec.displayName}」吗？此操作不可恢复。`,
    onConfirm: async () => {
      try {
        await apiClient.extension.customResource.delete(
          "sync.halo.run",
          "v1alpha1",
          "syncplatforms",
          record.metadata.name
        );
        toast.success("删除成功");
        fetchPlatforms();
      } catch (e: any) {
        toast.error("删除失败: " + (e.message || "未知错误"));
      }
    }
  });
};

onMounted(fetchPlatforms);
</script>

<template>
  <v-card title="平台管理" :loading="loading">
    <template #extra>
      <v-button type="secondary" @click="handleCreate">
        <template #icon>
          <Icon icon="ri:add-line" />
        </template>
        添加平台
      </v-button>
    </template>

    <v-table
      :columns="columns"
      :data-source="platforms"
      row-key="metadata.name"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'type'">
          <v-tag :type="getPlatformTypeTag(record.spec.platformType)">
            {{ getPlatformTypeLabel(record.spec.platformType) }}
          </v-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <v-tag :type="record.spec.enabled ? 'success' : 'default'">
            {{ record.spec.enabled ? '已启用' : '已禁用' }}
          </v-tag>
        </template>
        <template v-else-if="column.key === 'autoSync'">
          <v-tag :type="record.spec.rules?.autoSync ? 'info' : 'default'">
            {{ record.spec.rules?.autoSync ? '开启' : '关闭' }}
          </v-tag>
        </template>
        <template v-else-if="column.key === 'actions'">
          <v-space>
            <v-button size="small" type="link" @click="handleValidate(record)">
              验证
            </v-button>
            <v-button size="small" type="link" @click="handleEdit(record)">
              编辑
            </v-button>
            <v-button 
              size="small" 
              type="link" 
              @click="handleToggle(record)"
            >
              {{ record.spec.enabled ? '禁用' : '启用' }}
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

    <div v-if="platforms.length === 0 && !loading" class="empty-state">
      <Icon icon="ri:share-line" style="font-size: 48px; color: #ccc;" />
      <p>暂无平台配置</p>
      <v-button type="primary" @click="handleCreate">添加第一个平台</v-button>
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
  margin: 16px 0;
}
</style>
