<div align="center">

# 🍼 BabyLog

### 离线优先的宝宝照护记录、成长档案与家庭同步应用

**当前维护版本：1.9.1（开发中）** · 面向长期记录、家庭协作与安全保存

把喂养、辅食、营养补充、护理、睡眠、成长、相册和疫苗记录放在一起。  
手机端可独立离线使用；需要多设备共享时，可连接自己部署的 NAS 同步服务。

[![Android CI](https://github.com/rinzaki007/BabyLog/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/rinzaki007/BabyLog/actions/workflows/android.yml)
[![Latest Release](https://img.shields.io/github/v/release/rinzaki007/BabyLog?label=最新版本)](https://github.com/rinzaki007/BabyLog/releases/latest)
[![Android](https://img.shields.io/badge/平台-Android-3DDC84?logo=android&logoColor=white)](https://github.com/rinzaki007/BabyLog)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## ✨ 功能一览

| 模块 | 主要功能 |
| --- | --- |
| 🍼 **喂养记录** | 记录配方奶喂养时间、奶量与喂养情况 |
| 🧷 **日常护理** | 记录小便、大便时间，以及性状、颜色和量 |
| 🥣 **辅食记录** | 记录食物名称、可选食用量与进食情况，支持自定义食物 | 
| 🧴 **补充记录** | 记录维生素 AD、D3、铁剂、水、DHA、益生菌、乳糖酶等，可自定义项目与数量单位 |
| 🌙 **睡眠记录** | 记录睡眠时间，查看每日作息 |
| 📈 **成长与每日趋势** | 记录体重、身长、头围等数据；查看每日照护趋势、月度测量汇总与长期变化 |
| 🌱 **成长档案与回顾** | 成长里程碑、家庭纪念日、成长时间线与阶段回顾 |
| 📄 **成长报告** | 将成长记录整理为 PDF，便于长期留存与家庭分享 |
| 🖼️ **成长相册** | 按时间线浏览照片、查看沉浸式大图；缩略图以角标区分照片原图位于本地、缓存或云端 |
| 💉 **疫苗接种** | 查看接种安排，记录已完成的疫苗 |
| 🏡 **多设备同步** | 同一 BabyLog 账号在多台设备登录，共享同一份宝宝档案与记录 |
| 🖥️ **网页管理** | 通过浏览器登录 NAS 面板，查看记录、照片并管理备份 |
| 📦 **数据备份与恢复** | 默认导出记录数据；可选包含照片和头像；支持 SHA-256 媒体完整性校验、安全增量导入与旧版备份兼容 |
| 🔄 **应用更新** | 支持检查 NAS 内网更新源和 GitHub 官方更新源 |

## 🛠️ 1.9.1 维护重点

1.9.1 以稳定性、长期使用体验和数据处理效率为主，不以堆叠功能为目标：

- **同步摘要优化**：只读取完整性校验所需的记录标识与服务端版本，保持既有摘要格式和同步协议。
- **稳定分页**：时间线与成长相册使用时间戳和记录 ID 作为游标，降低新增记录对后续翻页的干扰。
- **加载失败恢复**：区分首次加载与继续加载失败，保留已加载内容并支持从原游标重试。
- **构建缓存**：启用 Gradle 构建缓存，继续保留 CI 已验证的 Kotlin 编译内存与单 Worker 配置。

以上属于维护版本的内部优化；正式发布版本及安装包请以 [GitHub Releases](https://github.com/rinzaki007/BabyLog/releases) 页面为准。

## 🌱 设计理念

- **离线优先**：不登录、不联网、不配置服务器，也可以在手机本地记录日常数据。
- **数据由自己掌握**：手机端使用本地数据库保存记录；需要共享时，由自己部署 NAS 同步服务。
- **同步按需开启**：不强制绑定第三方云服务，不依赖 Supabase。
- **同一账号多设备**：家庭内的手机使用同一个 BabyLog 账号和密码登录，共享同一份宝宝档案；不是每位家庭成员分别注册独立账号。
- **安全导入**：备份导入前先预览校验；新格式备份会校验照片和头像的 SHA-256 摘要；只新增目标端不存在的记录，已存在的记录会跳过，不覆盖现有数据。
- **长期可追溯**：成长趋势只汇总真实测量值，不插值、不推测；成长报告与纪念日回顾帮助家庭整理长期成长档案。
- **照片备份可选**：网页端默认只备份宝宝档案和记录数据；需要完整迁移时，可勾选照片和头像。照片较多时，数据备份能显著减小 ZIP 文件体积。

## 📱 获取与使用

1. 前往 [GitHub Releases](https://github.com/rinzaki007/BabyLog/releases) 获取 Android 安装包。
2. 安装后即可离线使用，创建宝宝档案并开始记录。
3. 如需多设备共享，先部署 NAS 同步服务；每台手机填写相同的服务器地址，并使用**同一个 BabyLog 账号和密码登录**。
4. 不使用家庭同步功能时，无需部署 Docker，也无需准备 NAS。

## 🏡 自建 NAS 同步

服务端位于 `server/`，使用 FastAPI、SQLite WAL 和 NAS 文件系统。仓库根目录提供 `docker-compose.yml`，可通过 Docker Compose 或 Dockge 部署。

| 项目 | 默认值 / 说明 |
| --- | --- |
| 容器监听端口 | `18765` |
| 默认主机端口 | `18765` |
| 数据目录 | 挂载到容器内 `/data` 的持久化目录 |
| 数据存储 | SQLite 数据库、照片、头像和应用更新文件 |
| 容器运行用户 | 完成目录准备后，以非 root 用户 UID/GID `10001` 运行 |

**快速部署：**

1. 将仓库下载到 NAS，进入项目目录。
2. 复制 `.env.example` 为 `.env`。
3. 将 `BABYLOG_JWT_SECRET` 替换为至少 32 位的随机密钥，并按需修改数据目录和主机端口。
4. 执行 `docker compose up -d --build`。Dockge 用户也可以导入根目录的 Compose 配置，填写相同的环境变量和持久化目录后启动。

Compose 核心配置如下（密钥务必自行生成，不要使用示例值）：

```yaml
services:
  babylog-sync:
    build:
      context: ./server
    image: babylog-sync:latest
    container_name: babylog-sync
    restart: unless-stopped
    environment:
      BABYLOG_DATA_DIR: /data
      BABYLOG_JWT_SECRET: ${BABYLOG_JWT_SECRET:?请先设置 BABYLOG_JWT_SECRET}
      BABYLOG_TOKEN_TTL_SECONDS: ${BABYLOG_TOKEN_TTL_SECONDS:-2592000}
      BABYLOG_MAX_PHOTO_BYTES: ${BABYLOG_MAX_PHOTO_BYTES:-20971520}
      # Exact reverse-proxy source IP(s); do not use *.
      BABYLOG_TRUSTED_PROXY_IPS: ${BABYLOG_TRUSTED_PROXY_IPS:-127.0.0.1}
    ports:
      - "${BABYLOG_PORT:-18765}:18765"
    volumes:
      - ${BABYLOG_DATA_PATH:-./data}:/data
```

升级服务端镜像时，务必保留原有数据卷和 `BABYLOG_JWT_SECRET`，不要删除 NAS 的 `/data` 持久化目录。建议通过 HTTPS 反向代理访问服务，不要将 SQLite 数据库文件或 NAS 管理端口直接暴露到公网。若使用反向代理，请按下方说明配置 `BABYLOG_TRUSTED_PROXY_IPS`，只信任实际连接到 BabyLog 的代理来源 IP，不要填写代理的上游目标地址，也不要使用 `*`。

### 网页面板与备份

新版备份采用 v2 清单，为照片和头像记录 SHA-256 摘要；导入预检会逐个验证媒体文件。早期 v1 备份仍保持兼容，但不具备文件级摘要校验。
NAS 服务启动后，可在浏览器打开服务地址进入网页面板，使用与 Android App **相同的账号和密码**登录。

- **查看数据**：查看宝宝档案、记录统计、记录列表和照片；支持分页与日期筛选。
- **默认数据备份**：导出宝宝档案和记录数据，不包含照片与头像，适合日常备份、迁移记录。
- **完整备份**：导出时勾选“包含照片和头像”，会把照片、头像一并打包，文件体积会更大。
- **导入前校验**：上传 ZIP 后先检查格式、内容和可导入数量，不会立即修改数据。
- **安全增量导入**：确认后只新增目标端不存在的记录；重复记录会跳过，不覆盖已有数据。照片和头像只在备份包含媒体文件时恢复。
- **上传限制**：网页备份 ZIP 导入上限为 512 MB；上传和解压校验采用流式处理，减少大文件导入时的内存占用。

> 如果预览显示“可新增 0 条”，表示目标端已经存在备份中的记录，通常无需再次确认导入。

详细说明：

- [产品需求与范围](docs/PRD.md)
- [家庭同步与 NAS 部署说明](docs/FAMILY_SYNC.md)
- [开发与 CI 约定](docs/DEVELOPMENT.md)（含正式 APK 发布、失败恢复与 NAS 更新判断）
- [Android 签名与发布配置](docs/RELEASE_SIGNING.md)

## 🧰 技术栈

| 部分 | 技术 |
| --- | --- |
| Android 客户端 | Kotlin、Jetpack Compose、Room、WorkManager |
| NAS 同步服务 | Python、FastAPI、SQLite WAL |
| 网页管理面板 | HTML、JavaScript、服务端 Cookie 会话 |
| 部署方式 | Docker Compose、Dockge |
| 版本构建 | GitHub Actions |

## 🌍 开源许可与贡献

BabyLog 以 **MIT License** 开源。你可以在遵守许可证条款的前提下自由使用、复制、修改、合并、发布、分发、再许可和销售本软件副本；分发软件或其重要部分时，请保留原版权声明和许可证文本。完整条款见 [LICENSE](LICENSE)。

欢迎提交 Issue 和 Pull Request，参与改进功能、修复问题与完善文档。贡献前请阅读 [贡献指南](CONTRIBUTING.md) 和 [开发与 CI 约定](docs/DEVELOPMENT.md)。涉及同步协议、数据库结构、照片或备份处理的改动，也请同步检查 [家庭同步说明](docs/FAMILY_SYNC.md)。

**第三方组件说明：** MIT 许可适用于本仓库作者拥有版权并纳入本项目的代码及文档；第三方依赖、字体、图标、图片、商标和其他素材仍分别适用其各自许可证或使用条款。使用或再分发时，请自行遵守相关要求。BabyLog 不授予对第三方品牌或服务的额外权利。

## 🛡️ 免责声明

软件按“现状”提供，不附带任何明示或默示保证。请根据自身环境评估并安全部署；尤其应妥善保护宝宝及家庭隐私数据、备份文件、账号凭据和服务器密钥。MIT License 的完整免责声明与责任限制以 [LICENSE](LICENSE) 为准。

## 🔐 隐私与安全提醒

- 请勿将真实宝宝档案、照片、账号密码、访问令牌或密钥提交到代码仓库。
- `BABYLOG_JWT_SECRET` 应由部署者自行生成并妥善保存；更换密钥会影响现有登录令牌，请勿在普通升级时随意更换。
- NAS 数据目录应使用持久化存储，并定期备份数据库与照片。
- 不要直接向公网开放数据库文件或 NAS 管理端口。
- 导出的备份文件包含个人家庭数据，请妥善保存，不要上传到不可信的公开位置。

---

<div align="center">

**BabyLog · 让每一次成长，都有迹可循。**

</div>
