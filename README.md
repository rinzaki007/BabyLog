<div align="center">

# 🍼 BabyLog

### 轻量、离线优先的宝宝成长记录与家庭同步应用

把喂养、护理、睡眠、成长、相册和疫苗记录放在一起。  
不依赖云账号也能独立使用；需要家庭共享时，可连接自己的 NAS。

[![Android CI](https://github.com/rinzaki007/BabyLog/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/rinzaki007/BabyLog/actions/workflows/android.yml)
[![Latest Release](https://img.shields.io/github/v/release/rinzaki007/BabyLog?label=最新版本)](https://github.com/rinzaki007/BabyLog/releases/latest)
[![Android](https://img.shields.io/badge/平台-Android-3DDC84?logo=android&logoColor=white)](https://github.com/rinzaki007/BabyLog)

</div>

---

## ✨ 功能一览

| 模块 | 主要功能 |
| --- | --- |
| 🍼 **喂养记录** | 记录配方奶时间与奶量，单位为毫升 |
| 🧷 **日常护理** | 记录小便、大便时间，以及性状、颜色和量 |
| 🌙 **睡眠记录** | 记录睡眠时间，查看每日作息 |
| 📈 **成长曲线** | 记录身高、体重等数据，查看变化趋势 |
| 🖼️ **成长相册** | 按时间浏览照片，查看大图与照片信息 |
| 💉 **疫苗接种** | 查看接种安排，记录已完成的疫苗 |
| 🏡 **家庭同步** | 同一账号登录的多台设备通过自建 NAS 同步档案、记录和照片 |
| 🔄 **应用更新** | 支持检查 NAS 内网更新源和 GitHub 官方更新源 |

## 🌱 设计理念

- **离线优先**：不登录、不联网、不配置服务器，也可以完整记录日常数据。
- **数据由自己掌握**：手机端使用本地数据库保存记录；家庭同步服务可部署在自己的 NAS。
- **同步按需开启**：不强制绑定第三方云服务，不依赖 Supabase。
- **同一账号多设备**：家庭内的手机使用同一个 BabyLog 账号登录并同步同一份宝宝档案，不需要为每位家庭成员分别注册账号。
- **谨慎处理数据**：同步冲突不会静默覆盖；升级服务端时保留 NAS 上的数据库和照片。

## 📱 获取与使用

1. 前往 [GitHub Releases](https://github.com/rinzaki007/BabyLog/releases) 获取 Android 安装包。
2. 安装后即可离线使用，先创建宝宝档案并开始记录。
3. 如需多设备共享，先部署 NAS 同步服务；每台手机填写相同的服务器地址，并使用**同一个 BabyLog 账号和密码登录**，共享同一份宝宝档案。不要为每位家庭成员分别注册账号。

> 不使用家庭同步功能时，无需部署 Docker，也无需准备 NAS。

## 🏡 自建 NAS 同步

服务端位于 `server/`，使用 FastAPI、SQLite WAL 和 NAS 文件系统。仓库根目录已提供 `docker-compose.yml`，下载项目后可直接通过 Docker Compose 或 Dockge 部署。

| 项目 | 默认值 / 说明 |
| --- | --- |
| 容器监听端口 | `18765` |
| 默认主机端口 | `18765` |
| 数据目录 | 通过持久化卷挂载到 `/data` |
| 数据存储 | SQLite 数据库与照片文件 |
| 容器运行用户 | 完成目录准备后，以非 root 用户 UID/GID `10001` 运行 |

**快速部署：** 将仓库下载到 NAS，进入项目目录，先复制 `.env.example` 为 `.env`，把 `BABYLOG_JWT_SECRET` 替换为至少 32 位的随机密钥，并按需修改 `BABYLOG_DATA_PATH`（默认 `./data`）和 `BABYLOG_PORT`（默认 `18765`），然后执行 `docker compose up -d --build`。Dockge 用户也可以直接导入仓库根目录的 `docker-compose.yml`，配置相同的环境变量和持久化目录后启动。

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
    ports:
      - "${BABYLOG_PORT:-18765}:18765"
    volumes:
      - ${BABYLOG_DATA_PATH:-./data}:/data
```

建议通过 HTTPS 反向代理访问服务，不要将 SQLite 数据库文件或 NAS 管理端口直接暴露到公网。

详细步骤与同步范围请查看：

- [家庭同步与 NAS 部署说明](docs/FAMILY_SYNC.md)
- [开发与 CI 约定](docs/DEVELOPMENT.md)

## 🧰 技术栈

| 部分 | 技术 |
| --- | --- |
| Android 客户端 | Kotlin、Jetpack Compose、Room、WorkManager |
| NAS 同步服务 | Python、FastAPI、SQLite WAL |
| 部署方式 | Docker Compose、Dockge |
| 版本构建 | GitHub Actions |

## 🛠️ 参与开发

Android 客户端与 NAS 服务端作为同一产品维护。提交变更时，CI 会分别验证 Android 构建、同步 API 测试和 Docker 镜像构建。

开始前建议先阅读 [开发与 CI 约定](docs/DEVELOPMENT.md)。涉及同步协议、数据库结构或照片处理的改动，也请同步检查 [家庭同步说明](docs/FAMILY_SYNC.md)。

## 🔐 隐私与安全提醒

- 请勿将真实宝宝档案、照片、账号密码、访问令牌或密钥提交到代码仓库。
- `BABYLOG_JWT_SECRET` 应由部署者自行生成并妥善保存，不能使用示例值作为生产密钥。
- NAS 数据目录应使用持久化存储，并定期备份数据库与照片。
- 不要直接向公网开放数据库文件或 NAS 管理端口。

---

<div align="center">

**BabyLog · 让每一次成长，都有迹可循。**

</div>
