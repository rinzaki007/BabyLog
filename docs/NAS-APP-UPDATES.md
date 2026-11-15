# BabyLog NAS APK 更新镜像

BabyLog NAS 同步服务同时提供 Android APK 更新镜像，不需要额外容器或端口。

## 工作方式

- NAS 启动后立即检查 GitHub 最新正式 Release，之后默认每 15 分钟检查一次。
- 优先使用 GitHub Releases API；遇到 API 限流时，读取公开 Releases Atom Feed 作为备用版本信息源。备用源按当前 Release 工作流生成的 `app-release.apk` 下载，并在 NAS 本地计算 SHA-256。
- 可选配置 `BABYLOG_GITHUB_TOKEN`，使用只读公开仓库所需权限的 Token，将 API 主限额从未认证请求通常每小时 60 次提高到认证请求通常每小时 5000 次。
- 遇到主限流时遵守 `X-RateLimit-Reset`，遇到次级限流时遵守 `Retry-After`；没有明确恢复时间时采用指数退避，避免短时间重复请求。
- 失败重试时间会保存在 `updates/status.json`，容器重启后仍然生效；连续失败时逐步延长等待时间，最长 1 小时。成功后清除普通失败退避。
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
- `GET /api/app-update/status`：NAS 镜像同步状态，包含失败次数、备用来源、API 冷却时间和下次重试时间。
- `POST /api/app-update/check`：手动触发一次检查；60 秒内重复触发会被节流，且不会绕过 GitHub 限流等待。
- `GET /api/app-update/download`：下载当前缓存 APK。

更新接口不要求登录，便于尚未登录同步账号的 App 获取更新。请仅通过可信的 NAS 内网地址、HTTPS 域名或 VPN 暴露这些接口。

## 配置

`BABYLOG_UPDATE_CHECK_SECONDS` 默认为 `900`（15 分钟），最小值自动限制为 300 秒。可在 Compose 的 environment 中覆盖。`BABYLOG_GITHUB_TOKEN` 为可选项，不配置时继续使用公开 API 和 Atom 备用源。镜像只同步 GitHub 最新正式 Release，不同步草稿或预发布版本。

外网手机无法访问 NAS 时，App 会回退官方 GitHub 更新地址；若需要外网也走 NAS，请为 NAS 配置 HTTPS 域名或 VPN。此功能不会更改账号、JWT Secret、照片或数据库存储路径。
