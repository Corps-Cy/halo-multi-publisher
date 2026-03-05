<template>
  <v-page :header-title="isEdit ? '编辑平台' : '添加平台'" header-hero-icon="ri:share-circle-line">
    <template #header-actions>
      <v-button @click="$router.back()">取消</v-button>
      <v-button type="primary" :loading="saving" @click="save">
        保存
      </v-button>
    </template>

    <v-card>
      <v-form :model="form" @finish="save">
        <!-- 基本信息 -->
        <v-form-item label="平台类型" name="platformType" required>
          <v-select v-model:value="form.spec.platformType" :disabled="isEdit" @change="onPlatformChange">
            <v-select-option value="wechat">
              <div class="flex items-center gap-2">
                <Icon icon="ri:wechat-fill" style="color: #07c160" />
                微信公众号
              </div>
            </v-select-option>
            <v-select-option value="juejin">
              <div class="flex items-center gap-2">
                <Icon icon="simple-icons:juejin" style="color: #1e80ff" />
                掘金
              </div>
            </v-select-option>
            <v-select-option value="zhihu" disabled>
              <div class="flex items-center gap-2">
                <Icon icon="ri:zhihu-fill" style="color: #0084ff" />
                知乎（开发中）
              </div>
            </v-select-option>
          </v-select>
        </v-form-item>

        <v-form-item label="显示名称" name="displayName" required>
          <v-input v-model:value="form.spec.displayName" placeholder="例如：我的公众号" />
        </v-form-item>

        <v-form-item label="启用状态">
          <v-switch v-model="form.spec.enabled" />
        </v-form-item>

        <!-- 微信公众号配置 -->
        <template v-if="form.spec.platformType === 'wechat'">
          <v-divider>微信公众号配置</v-divider>
          
          <v-form-item label="App ID" name="appId" required>
            <v-input v-model:value="form.spec.credentials.appId" placeholder="从微信公众平台获取" />
          </v-form-item>
          
          <v-form-item label="App Secret" name="appSecret" required>
            <v-input 
              v-model:value="form.spec.credentials.appSecret" 
              type="password"
              placeholder="从微信公众平台获取"
            />
          </v-form-item>

          <v-form-item label="默认封面图 Media ID" name="defaultThumbMediaId">
            <v-input 
              v-model:value="form.spec.credentials.defaultThumbMediaId" 
              placeholder="（可选）在公众号后台上传封面图后获取"
            />
            <template #extra>
              <span class="text-xs text-gray-500">
                微信要求文章必须有封面图。你可以：
                1) 在公众号后台上传封面图并获取 media_id；
                2) 留空，插件会尝试上传文章封面图
              </span>
            </template>
          </v-form-item>

          <v-form-item>
            <v-button type="secondary" size="small" @click="testWeChatConnection">
              <template #icon><Icon icon="ri:link" /></template>
              测试连接
            </v-button>
          </v-form-item>
        </template>

        <!-- 掘金配置 -->
        <template v-if="form.spec.platformType === 'juejin'">
          <v-divider>掘金配置</v-divider>
          
          <v-form-item label="Cookie" name="cookie" required>
            <v-textarea 
              v-model:value="form.spec.credentials.cookie" 
              :rows="3"
              placeholder="从浏览器开发者工具复制 Cookie"
            />
            <template #extra>
              <span class="text-xs text-gray-500">
                获取方法：登录掘金 → 打开开发者工具(F12) → Network → 找到任意请求 → 复制 Cookie 值
              </span>
            </template>
          </v-form-item>

          <v-form-item>
            <v-button type="secondary" size="small" @click="testJuejinConnection">
              <template #icon><Icon icon="ri:link" /></template>
              测试连接
            </v-button>
          </v-form-item>
        </template>

        <!-- 同步规则 -->
        <v-divider>同步规则</v-divider>

        <v-form-item label="自动同步">
          <v-switch v-model="form.spec.rules.autoSync" />
          <template #extra>
            <span class="text-xs text-gray-500">
              开启后，文章发布时自动同步到此平台
            </span>
          </template>
        </v-form-item>

        <v-form-item label="同步分类">
          <v-select v-model:value="form.spec.rules.categories" mode="multiple" placeholder="留空表示同步所有分类">
            <v-select-option v-for="cat in categories" :key="cat.metadata.name" :value="cat.metadata.name">
              {{ cat.spec.displayName }}
            </v-select-option>
          </v-select>
          <template #extra>
            <span class="text-xs text-gray-500">
              仅同步选定分类的文章（留空则同步所有）
            </span>
          </template>
        </v-form-item>

        <v-form-item label="转换 Markdown 格式">
          <v-switch v-model="form.spec.rules.convertMarkdown" />
          <template #extra>
            <span class="text-xs text-gray-500">
              将 Markdown 转换为平台支持的格式
            </span>
          </template>
        </v-form-item>

        <v-form-item label="包含封面图">
          <v-switch v-model="form.spec.rules.includeCover" />
        </v-form-item>

        <v-form-item label="失败时重试">
          <v-switch v-model="form.spec.rules.retryOnFailure" />
        </v-form-item>

        <v-form-item v-if="form.spec.rules.retryOnFailure" label="最大重试次数">
          <v-input-number v-model:value="form.spec.rules.maxRetries" :min="1" :max="10" />
        </v-form-item>
      </v-form>
    </v-card>
  </v-page>
</template>

<script lang="ts" setup>
import { ref, computed, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { apiClient } from "@halo-dev/console-shared";
import { useToast } from "@halo-dev/console-shared";

const route = useRoute();
const router = useRouter();
const toast = useToast();

const isEdit = computed(() => !!route.params.name);
const saving = ref(false);
const categories = ref<any[]>([]);

const form = ref({
  apiVersion: "sync.halo.run/v1alpha1",
  kind: "SyncPlatform",
  metadata: {
    name: "",
  },
  spec: {
    platformType: "wechat",
    displayName: "",
    enabled: true,
    credentials: {
      appId: "",
      appSecret: "",
      defaultThumbMediaId: "",
      cookie: "",
    },
    rules: {
      autoSync: false,
      categories: [],
      convertMarkdown: true,
      includeCover: true,
      retryOnFailure: true,
      maxRetries: 3,
    },
  },
});

const onPlatformChange = () => {
  // 清空其他平台的凭证
  form.value.spec.credentials = {
    appId: "",
    appSecret: "",
    defaultThumbMediaId: "",
    cookie: "",
  };
};

const fetchCategories = async () => {
  try {
    const { data } = await apiClient.extension.customResource.list(
      "content.halo.run",
      "v1alpha1",
      "categories"
    );
    categories.value = data.items || [];
  } catch (e) {
    console.error("Failed to fetch categories", e);
  }
};

const fetchPlatform = async () => {
  if (!isEdit.value) return;

  try {
    const { data } = await apiClient.extension.customResource.get(
      "sync.halo.run",
      "v1alpha1",
      "syncplatforms",
      route.params.name as string
    );
    
    // 合并数据
    form.value = {
      ...form.value,
      ...data,
      spec: {
        ...form.value.spec,
        ...data.spec,
        credentials: {
          ...form.value.spec.credentials,
          ...data.spec.credentials,
        },
        rules: {
          ...form.value.spec.rules,
          ...data.spec.rules,
        },
      },
    };
  } catch (e) {
    console.error("Failed to fetch platform", e);
    toast.error("加载平台配置失败");
    router.back();
  }
};

const testWeChatConnection = async () => {
  if (!form.value.spec.credentials.appId || !form.value.spec.credentials.appSecret) {
    toast.warning("请先填写 App ID 和 App Secret");
    return;
  }

  toast.info("正在测试连接...");
  try {
    // 临时保存并测试
    const tempName = "temp-test-" + Date.now();
    const testData = JSON.parse(JSON.stringify(form.value));
    testData.metadata.name = tempName;

    await apiClient.extension.customResource.create(
      "sync.halo.run",
      "v1alpha1",
      "syncplatforms",
      testData
    );

    const response = await fetch(
      `/apis/sync.halo.run/v1alpha1/syncplatforms/${tempName}/test`,
      { method: "POST" }
    );
    const result = await response.json();

    // 删除临时数据
    await apiClient.extension.customResource.delete(
      "sync.halo.run",
      "v1alpha1",
      "syncplatforms",
      tempName
    );

    if (result.connected) {
      toast.success("连接成功！");
    } else {
      toast.error("连接失败: " + (result.message || "未知错误"));
    }
  } catch (e) {
    console.error("Test failed", e);
    toast.error("测试失败");
  }
};

const testJuejinConnection = async () => {
  if (!form.value.spec.credentials.cookie) {
    toast.warning("请先填写 Cookie");
    return;
  }

  toast.info("正在测试连接...");
  try {
    const tempName = "temp-test-" + Date.now();
    const testData = JSON.parse(JSON.stringify(form.value));
    testData.metadata.name = tempName;
    testData.spec.platformType = "juejin";

    await apiClient.extension.customResource.create(
      "sync.halo.run",
      "v1alpha1",
      "syncplatforms",
      testData
    );

    const response = await fetch(
      `/apis/sync.halo.run/v1alpha1/syncplatforms/${tempName}/test`,
      { method: "POST" }
    );
    const result = await response.json();

    await apiClient.extension.customResource.delete(
      "sync.halo.run",
      "v1alpha1",
      "syncplatforms",
      tempName
    );

    if (result.connected) {
      toast.success("连接成功！");
    } else {
      toast.error("连接失败: " + (result.message || "未知错误"));
    }
  } catch (e) {
    console.error("Test failed", e);
    toast.error("测试失败");
  }
};

const save = async () => {
  saving.value = true;

  try {
    // 生成 name
    if (!form.value.metadata.name) {
      form.value.metadata.name = form.value.spec.platformType + "-" + Date.now();
    }

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

    router.push("/sync-platforms");
  } catch (e: any) {
    console.error("Failed to save", e);
    toast.error("保存失败: " + (e.message || "未知错误"));
  } finally {
    saving.value = false;
  }
};

onMounted(() => {
  fetchCategories();
  fetchPlatform();
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
.text-xs {
  font-size: 0.75rem;
}
.text-gray-500 {
  color: #6b7280;
}
</style>
