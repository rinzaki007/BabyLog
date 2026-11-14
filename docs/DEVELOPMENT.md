# 开发与 CI 约定

BabyLog Android 与 NAS 同步服务属于同一个产品版本，必须一起维护、一起验证。

## 主分支 CI

每次向 main 推送或创建 PR 时，CI 并行执行：

1. Android：构建 debug APK。
2. Sync API：运行 FastAPI 接口测试，覆盖注册登录、家庭邀请、成员隔离、增量同步、幂等和照片上传下载。
3. Docker：构建 NAS 同步服务镜像，并校验 Compose 配置。

三个任务全部通过才视为该提交通过 CI。Android APK 作为 Actions artifact 上传；Docker 镜像目前只做 CI 构建验证，不会因每次代码变更自动推送 GHCR 或触发 NAS 更新。

## API 兼容原则

- Android 和服务端共用 `docs/FAMILY_SYNC.md` 中定义的 API 契约。
- 破坏性 API 变更必须同步更新 Android 客户端、服务端和契约测试。
- 数据库迁移必须保留已有本地宝宝档案、事件和照片。
- 离线记录优先写入 Room；服务器未配置时不发起网络请求。
- Docker 数据目录必须持久化；升级不得覆盖数据库和照片。
- 未通过 Android 构建、API 测试或 Docker 构建的变更，不应标记为完成。
