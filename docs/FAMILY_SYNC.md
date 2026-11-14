# BabyLog 自建家庭同步方案

## 产品原则

- Android 默认完全离线可用，不要求注册、登录或填写服务器地址。
- Room 始终是手机端读写入口；同步是用户主动配置的可选能力。
- NAS 运行独立 Docker 同步服务，不依赖 Supabase 或其他云平台。
- 两位家长分别使用自己的账号加入同一个家庭空间。

## Docker 部署

服务端位于 `server/`，采用 FastAPI、SQLite WAL 和 NAS 文件系统。容器内部监听端口 **18765**，Compose 默认映射主机端口 **18765**。如 NAS 主机端口冲突，只需修改 `BABYLOG_PORT`。

1. 复制 `.env.example` 为 `.env`。
2. 设置唯一的随机 `BABYLOG_JWT_SECRET`，至少 32 字符，不能提交到 Git。
3. 将 `BABYLOG_DATA_PATH` 改为 NAS 持久化目录。
4. 在 Dockge 部署 Compose。
5. 将专用 HTTPS 域名反向代理到 NAS 的 18765 端口。
6. 检查 `https://你的域名/health`。

容器启动时会准备挂载目录权限，随后以 UID/GID 10001 的非 root 用户运行 API。不要暴露数据库文件或 NAS 管理端口。备份应使用 SQLite 在线备份方式，并同时备份照片目录。

## API

- `POST /api/auth/register`、`POST /api/auth/login`
- `GET /api/me`
- `POST /api/families`
- `POST /api/families/{family_id}/invites`
- `POST /api/families/join`
- `POST /api/sync/push`
- `GET /api/sync/pull?family_id=...&cursor=0`
- `POST /api/photos/upload`
- `GET /api/photos/{photo_id}?family_id=...`

所有家庭数据和照片接口都要求 Bearer Token，并在服务端检查家庭成员权限。API 文档端点在生产服务中关闭。

## Android 同步范围

当前 Android 已接入服务器地址配置、账号注册/登录、家庭创建/邀请码加入、Room outbox 和 WorkManager 后台同步。宝宝档案与喂养、护理、睡眠、成长等事件可进行双向同步。首次创建家庭时，会将当前手机的宝宝档案和未删除事件加入首次上传队列；加入已有家庭的设备以拉取家庭数据为主。

成长相册照片已接入 NAS 原图上传、跨设备下载和删除墓碑同步；同步设置页显示待同步数量、冲突数量、最近成功同步时间和最近错误。头像文件仍保留设备本地，不上传。冲突记录目前会保留在队列中并显示数量，逐条解决界面仍在后续阶段。

## 同步协议

每次本地变更生成唯一 operation_id，包含 entity_type、entity_id、operation、payload 和 base_version。服务端以家庭 + operation_id 保证幂等，以记录版本检测并发修改。拉取使用服务端递增 cursor。发生 version_conflict 时客户端保留冲突队列，不静默覆盖。删除通过 delete 操作形成同步墓碑。
