# BabyLog NAS APK 更新镜像

BabyLog NAS 同步服务同时提供 Android APK 更新镜像，不需要额外容器或端口。

## 工作方式

- NAS 启动后立即检查 GitHub 最新正式 Release，之后默认每 15 分钟检查一次。
- 发现新版本后下载 APK 到数据目录的 `updates/` 子目录，并计算 SHA-256。
- 下载完成后原子替换版本元数据；保留当前版本和上一个 APK。
- App 更新页优先检查已配置的 NAS 同步地址；NAS 不可用或没有更新时回退 GitHub 官方 Release。
- 安装前 App 校验文件大小和 SHA-256，最后仍由 Android 系统安装器验证 APK 签名并请求用户确认。

## 数据目录

沿用现有 `BABYLOG_DATA_DIR` 挂载，不需要迁移现有数据：

```text
/data/
  babylog.db
  photos/
  avatars/
  updates/
    latest.json
    status.json
    BabyLog-vX.Y.Z.apk
```

## 接口

- `GET /api/app-update/latest`：最新版本元数据及镜像同步状态。
- `GET /api/app-update/status`：NAS 镜像同步状态。
- `GET /api/app-update/download`：下载当前缓存 APK。

更新接口不要求登录，便于尚未登录同步账号的 App 获取更新。请仅通过可信的 NAS 内网地址、HTTPS 域名或 VPN 暴露这些接口。

## 配置

`BABYLOG_UPDATE_CHECK_SECONDS` 默认为 `900`（15 分钟），最小值自动限制为 300 秒。可在 Compose 的 environment 中覆盖。镜像只同步 GitHub 最新正式 Release，不同步草稿或预发布版本。

外网手机无法访问 NAS 时，App 会回退官方 GitHub 更新地址；若需要外网也走 NAS，请为 NAS 配置 HTTPS 域名或 VPN。此功能不会更改账号、JWT Secret、照片或数据库存储路径。
