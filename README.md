# Halo Multi-Publisher

**Halo 多平台文章同步插件** - 将 Halo 博客文章自动同步到微信公众号、掘金、知乎等平台。

## ✨ 功能特性

- 🔗 **多平台支持** - 微信公众号、掘金（更多平台开发中）
- 🔄 **自动同步** - 文章发布后自动同步到配置的平台
- 📝 **格式转换** - 自动将 Markdown 转换为各平台支持的格式
- 📊 **状态追踪** - 实时查看同步状态和历史记录
- ⚙️ **灵活配置** - 支持按分类、标签过滤同步

## 📦 安装

### 方式一：从 Release 下载

1. 前往 [Releases](https://github.com/Corps-Cy/halo-multi-publisher/releases) 页面
2. 下载最新版本的 `.jar` 文件
3. 在 Halo 后台 -> 插件 -> 安装插件，上传 jar 文件

### 方式二：本地构建

```bash
# 克隆仓库
git clone https://github.com/Corps-Cy/halo-multi-publisher.git
cd halo-multi-publisher

# 构建
./gradlew build

# 生成的插件位于 build/libs/ 目录
```

## ⚙️ 配置

### 1. 微信公众号

1. 登录 [微信公众平台](https://mp.weixin.qq.com/)
2. 获取 AppID 和 AppSecret（设置与开发 -> 基本配置）
3. 在插件配置中填写：
   - `appId`: 你的 AppID
   - `appSecret`: 你的 AppSecret
   - `defaultThumbMediaId`: （可选）默认封面图的 media_id

### 2. 掘金

1. 登录掘金网站
2. 打开开发者工具 -> Network
3. 找到任意请求，复制 Cookie 值
4. 在插件配置中填写：
   - `cookie`: 你的掘金 Cookie

## 🚀 使用方法

### 手动同步

1. 进入 Halo 后台 -> 多平台同步
2. 点击"添加平台"配置目标平台
3. 在文章编辑页面，点击"同步到其他平台"按钮

### 自动同步

1. 在平台配置中开启"自动同步"
2. （可选）配置分类过滤，只同步特定分类的文章
3. 发布文章后将自动触发同步

## 🏗️ 项目结构

```
halo-multi-publisher/
├── src/main/
│   ├── java/run/halo/sync/publisher/
│   │   ├── adapter/                 # 平台适配器
│   │   │   ├── PlatformAdapter.java # 适配器接口
│   │   │   └── impl/
│   │   │       ├── WeChatPlatformAdapter.java
│   │   │       └── JuejinPlatformAdapter.java
│   │   ├── extension/               # Extension 定义
│   │   │   ├── SyncPlatform.java    # 平台配置
│   │   │   └── SyncTask.java        # 同步任务
│   │   ├── reconciler/              # Reconciler 实现
│   │   │   ├── SyncTaskReconciler.java
│   │   │   └── PostPublishReconciler.java
│   │   └── service/                 # 服务层
│   └── resources/
│       ├── plugin.yaml
│       ├── extensions/
│       │   └── role.yaml
│       └── console/                 # 前端 UI
│           └── src/
│               └── index.ts
├── build.gradle
└── README.md
```

## 📚 API 说明

### Extension 资源

#### SyncPlatform（同步平台配置）

```yaml
apiVersion: sync.halo.run/v1alpha1
kind: SyncPlatform
metadata:
  name: my-wechat
spec:
  platformType: wechat
  displayName: 我的公众号
  credentials:
    appId: wx1234567890
    appSecret: secret123
  enabled: true
  rules:
    autoSync: true
    categories:
      - 技术
      - 教程
```

#### SyncTask（同步任务）

```yaml
apiVersion: sync.halo.run/v1alpha1
kind: SyncTask
metadata:
  name: task-001
spec:
  postName: my-post
  platformName: my-wechat
  action: create  # create, update, delete
status:
  phase: success  # pending, running, success, failed
  externalId: media_id_123
  externalUrl: https://mp.weixin.qq.com/s/xxx
```

## 🔧 开发指南

### 环境要求

- JDK 21+
- Gradle 8.5+
- Node.js 18+（前端构建）
- Halo 2.20+

### 本地开发

```bash
# 启动开发模式（自动重载）
./gradlew haloServer

# 仅构建后端
./gradlew build

# 构建前端
cd src/main/resources/console
pnpm install
pnpm build
```

### 添加新平台适配器

1. 实现 `PlatformAdapter` 接口

```java
@Component
public class MyPlatformAdapter implements PlatformAdapter {
    
    @Override
    public PlatformType getPlatformType() {
        return PlatformType.MY_PLATFORM;
    }
    
    @Override
    public Mono<PublishResult> publish(ArticleContent content, SyncPlatform platform) {
        // 实现发布逻辑
    }
    
    // ... 其他方法
}
```

2. 在 `SyncPlatform.PlatformType` 枚举中添加新类型

3. 注册为 Spring Bean，会自动被 `SyncTaskReconciler` 发现

## 🐛 常见问题

### Q: 微信公众号同步失败，提示需要封面图？

A: 微信公众号要求文章必须有封面图。你可以：
1. 在公众号后台上传一张默认封面，获取 `thumb_media_id`
2. 在平台配置中设置 `defaultThumbMediaId`
3. 确保文章有封面图，插件会自动上传

### Q: 掘金 Cookie 过期怎么办？

A: 掘金的 Cookie 有效期较长，但可能会过期。如果同步失败：
1. 重新登录掘金
2. 获取新的 Cookie
3. 更新平台配置

### Q: 同步任务一直显示"执行中"？

A: 检查 Halo 日志，可能是：
1. 外部平台 API 超时
2. 网络连接问题
3. 凭证失效

## 📝 更新日志

### v1.0.0-SNAPSHOT

- ✅ 基础框架搭建
- ✅ 微信公众号同步支持
- ✅ 掘金同步支持
- ✅ 自动同步功能
- ✅ 前端管理界面

## 🤝 贡献

欢迎贡献代码、报告问题或提出建议！

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

## 📄 许可证

本项目基于 [Apache-2.0](LICENSE) 许可证开源。

## 🙏 致谢

- [Halo](https://halo.run) - 优秀的博客平台
- [wechat-api.ts](../skills/scripts/wechat-api.ts) - 微信公众号 API 参考
- [wechat-formatter-fixed.ts](../skills/scripts/wechat-formatter-fixed.ts) - 微信格式化参考
