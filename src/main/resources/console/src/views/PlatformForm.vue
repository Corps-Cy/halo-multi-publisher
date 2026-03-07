<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import { apiClient } from "@halo-dev/console-shared";
import { useToast } from "@halo-dev/console-shared";
import { useRouter, useRoute } from "vue-router";

const router = useRouter();
const route = useRoute();
const toast = useToast();

const isEdit = computed(() => !!route.params.name);
const loading = ref(false);
const saving = ref(false);

const form = ref({
  metadata: {
    name: "",
    annotations: {}
  },
  spec: {
    platformType: "WECHAT",
    displayName: "",
    credentials: {} as Record<string, string>,
    enabled: true,
    rules: {
      autoSync: false,
      categories: [] as string[],
      tags: [] as string[]
    }
  }
});

const weChatFields = [
  { key: "appId", label: "AppId", placeholder: "微信公众号 AppId" },
  { key: "appSecret", label: "AppSecret", placeholder: "微信公众号 AppSecret", type: "password" },
  { key: "defaultThumbMediaId", label: "默认封面 MediaId", placeholder: "可选，图文消息封面" }
];

const juejinFields = [
  { key: "cookie", label: "Cookie", placeholder: "掘金网站 Cookie", type: "textarea" }
];

const currentFields = computed(() => {
  return form.value.spec.platformType === "WECHAT" ? weChatFields : juejinFields;
});

const fetchPlatform = async (name: string) => {
  loading.value = true;
  try {
    const { data } = await apiClient.extension.customResource.get(
      "sync.halo.run",
      "v1alpha1",
      "syncplatforms",
      name
    );
    form.value = data;
  } catch (e: any) {
    toast.error("加载平台配置失败: " + (e.message || "未知错误"));
    router.back();
  } finally {
    loading.value = false;
  }
};

const handleSubmit = async () => {
  // 验证
  if (!form.value.spec.displayName) {
    toast.warning("请输入显示名称");
    return;
  }
  if (!form.value.metadata.name) {
    toast.warning("请输入唯一标识");
    return;
  }

  saving.value = true;
  try {
    if (isEdit.value) {
      await apiClient.extension.customResource.update(
        "sync.halo.run",
        "v1alpha1",
        "syncplatforms",
        form.value.metadata.name,
        form.value
      );
      toast.success("更新成功");
    } else {
      await apiClient.extension.customResource.create(
        "sync.halo.run",
        "v1alpha1",
        "syncplatforms",
        form.value
      );
      toast.success("创建成功");
    }
    router.push("/multi-publisher/platforms");
  } catch (e: any) {
    toast.error((isEdit.value ? "更新" : "创建") + "失败: " + (e.message || "未知错误"));
  } finally {
    saving.value = false;
  }
};

const handleCancel = () => {
  router.back();
};

const generateName = () => {
  if (!form.value.metadata.name && form.value.spec.displayName) {
    form.value.metadata.name = form.value.spec.displayName
      .toLowerCase()
      .replace(/[^a-z0-9-]/g, '-')
      .replace(/-+/g, '-')
      .replace(/^-|-$/g, '');
  }
};

onMounted(() => {
  const name = route.params.name as string;
  if (name) {
    fetchPlatform(name);
  }
});
</script>

<template>
  <v-card :title="isEdit ? '编辑平台' : '添加平台'" :loading="loading">
    <div class="form-container">
      <!-- 基本信息 -->
      <div class="form-section">
        <h3>基本信息</h3>
        
        <div class="form-item">
          <label class="form-label required">平台类型</label>
          <v-radio-group v-model:value="form.spec.platformType">
            <v-radio value="WECHAT">微信公众号</v-radio>
            <v-radio value="JUEJIN">掘金</v-radio>
          </v-radio-group>
        </div>

        <div class="form-item">
          <label class="form-label required">显示名称</label>
          <v-input
            v-model:value="form.spec.displayName"
            placeholder="如：我的公众号"
            @blur="generateName"
          />
        </div>

        <div class="form-item">
          <label class="form-label required">唯一标识</label>
          <v-input
            v-model:value="form.metadata.name"
            placeholder="如：my-wechat"
            :disabled="isEdit"
          />
          <span class="form-hint">只能包含小写字母、数字和连字符</span>
        </div>

        <div class="form-item">
          <label class="form-label">启用状态</label>
          <v-switch v-model:value="form.spec.enabled" />
        </div>
      </div>

      <!-- 凭证配置 -->
      <div class="form-section">
        <h3>凭证配置</h3>
        
        <div
          v-for="field in currentFields"
          :key="field.key"
          class="form-item"
        >
          <label class="form-label">{{ field.label }}</label>
          <v-input
            v-if="field.type !== 'textarea'"
            v-model:value="form.spec.credentials[field.key]"
            :placeholder="field.placeholder"
            :type="field.type === 'password' ? 'password' : 'text'"
          />
          <v-textarea
            v-else
            v-model:value="form.spec.credentials[field.key]"
            :placeholder="field.placeholder"
            :rows="3"
          />
        </div>
      </div>

      <!-- 同步规则 -->
      <div class="form-section">
        <h3>同步规则</h3>
        
        <div class="form-item">
          <label class="form-label">自动同步</label>
          <v-switch v-model:value="form.spec.rules.autoSync" />
          <span class="form-hint">开启后，新发布的文章会自动同步到此平台</span>
        </div>

        <div class="form-item">
          <label class="form-label">分类过滤</label>
          <v-input
            v-model:value="form.spec.rules.categories"
            placeholder="留空表示全部，多个用逗号分隔"
          />
          <span class="form-hint">仅同步指定分类的文章</span>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="form-actions">
        <v-button @click="handleCancel">取消</v-button>
        <v-button type="primary" :loading="saving" @click="handleSubmit">
          {{ isEdit ? '更新' : '创建' }}
        </v-button>
      </div>
    </div>
  </v-card>
</template>

<style scoped>
.form-container {
  max-width: 800px;
  padding: 24px;
}

.form-section {
  margin-bottom: 32px;
}

.form-section h3 {
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid #eee;
  font-size: 16px;
  font-weight: 600;
}

.form-item {
  margin-bottom: 16px;
}

.form-label {
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
}

.form-label.required::after {
  content: ' *';
  color: #f5222d;
}

.form-hint {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #999;
}

.form-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  padding-top: 24px;
  border-top: 1px solid #eee;
}
</style>
