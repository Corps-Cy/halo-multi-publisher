<script setup lang="ts">
import { ref, onMounted } from "vue";
import { apiClient } from "@halo-dev/console-shared";
import { useToast } from "@halo-dev/console-shared";

const toast = useToast();
const platforms = ref([]);
const loading = ref(false);
const createModalVisible = ref(false);

const columns = [
  { title: "名称", dataIndex: "spec.displayName", key: "name" },
  { title: "平台类型", dataIndex: "spec.platformType", key: "type" },
  { title: "状态", dataIndex: "status.phase", key: "status" },
  { title: "自动同步", dataIndex: "spec.rules.autoSync", key: "autoSync" },
  { title: "操作", key: "actions" }
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
  } catch (e) {
    toast.error("加载平台列表失败");
    console.error(e);
  } finally {
    loading.value = false;
  }
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
      toast.warning("凭证验证失败");
    }
  } catch (e) {
    toast.error("验证请求失败");
  }
};

const handleDelete = async (record: any) => {
  try {
    await apiClient.extension.customResource.delete(
      "sync.halo.run",
      "v1alpha1",
      "syncplatforms",
      record.metadata.name
    );
    toast.success("删除成功");
    fetchPlatforms();
  } catch (e) {
    toast.error("删除失败");
  }
};

onMounted(fetchPlatforms);
</script>

<template>
  <v-card title="平台管理" :loading="loading">
    <template #extra>
      <v-button type="secondary" @click="createModalVisible = true">
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
          <v-tag v-if="record.spec.platformType === 'WECHAT'">微信公众号</v-tag>
          <v-tag v-else-if="record.spec.platformType === 'JUEJIN'">掘金</v-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <v-tag :type="record.status?.phase === 'Ready' ? 'success' : 'warning'">
            {{ record.status?.phase || 'Unknown' }}
          </v-tag>
        </template>
        <template v-else-if="column.key === 'autoSync'">
          <v-tag :type="record.spec.rules?.autoSync ? 'success' : 'default'">
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
            <v-button size="small" type="link" danger @click="handleDelete(record)">
              删除
            </v-button>
          </v-space>
        </template>
      </template>
    </v-table>
  </v-card>
</template>
