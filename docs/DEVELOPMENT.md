# 开发与 CI 约定

BabyLog Android 客户端、NAS 同步服务和网页管理面板属于同一个产品版本，必须一起维护、一起验证。

## 主分支 CI

向 `main` 推送或创建 PR 时，CI 并行执行：

1. **Android 单元测试与构建**：运行 JVM / Robolectric 测试并构建 debug APK，覆盖可在 JVM 环境验证的 Room、统计逻辑、同步队列及加密相关逻辑。
2. **Sync API**：运行 FastAPI 接口测试，覆盖认证、家庭数据访问、增量同步、幂等、照片上传下载、网页面板、备份导出与安全导入。
3. **Docker**：构建 NAS 同步服务镜像，执行容器健康检查并校验 Compose 配置。

以上 Android、NAS API 与 Docker 检查全部通过，才视为该提交通过 CI。当前 CI 不要求启动 Android Emulator；Android Keystore 等依赖真实 Android 系统的行为仍需在目标设备上验证。Android APK 作为 Actions artifact 上传；Docker 镜像由 CI 构建验证，不代表 NAS 已自动更新。除非专门配置发布流程，合并代码后仍需由部署者在 NAS / Dockge 更新容器。

## API 与数据兼容原则

- Android 和服务端共用 `docs/FAMILY_SYNC.md` 中定义的 API 契约。
- Android 现有 Bearer Token 同步 API 必须保持兼容；网页面板使用独立的 Cookie 会话与 CSRF 来源校验。
- 破坏性 API 变更必须同步更新 Android 客户端、服务端和契约测试。
- 数据库迁移必须保留已有本地宝宝档案、事件和照片。
- 离线记录优先写入 Room；服务器未配置时不发起网络请求。
- Docker 数据目录必须持久化；升级不得覆盖数据库、照片和头像。
- 备份导入必须保持增量新增原则：跳过目标端已存在的记录，不覆盖或删除现有数据。
- ZIP 导入应在临时文件中流式接收与校验，限制上传体积、条目数量、解压体积和媒体路径，避免将整个归档一次性载入内存。
- 数据备份默认不包含照片和头像；完整媒体备份必须由用户主动选择。

## 依赖维护

Dependabot 每周检查 Gradle / Android 依赖、`server/` 下的 Python 依赖以及 GitHub Actions 版本，并以 Pull Request 提交更新。依赖升级应先通过本仓库完整 CI，再合并；不要为了追新版本跳过兼容性验证。

## Android 正式发布流程

正式 Android APK 发布由 `.github/workflows/release.yml`（GitHub Actions：**BabyLog Android Release**）负责。它与 PR / main 的日常 CI 是两套不同流程：

1. **先完成代码整合**：所有变更通过 PR CI，合并到 `main`。不要把 PR 的 debug APK 或 CI Docker 镜像当作正式发布产物。
2. **确认版本与发布内容**：检查 `app/build.gradle.kts` 的默认 `appVersionName` / `appVersionCode`，并检查 README、CHANGELOG 和用户可见更新说明。正式发布时使用明确的版本号（例如 `1.9.0`）。
3. **检查签名 Secrets**：仓库 Actions Secrets 必须配置 `BABYLOG_KEYSTORE_BASE64`、`BABYLOG_KEYSTORE_PASSWORD`、`BABYLOG_KEY_ALIAS`、`BABYLOG_KEY_PASSWORD`。工作流会先验证这些值并解码 keystore；不要把 keystore 或密码提交到仓库。
4. **触发 Release**：在 Actions 中选择 **BabyLog Android Release**，点击 **Run workflow**，选择 `main`，填写 `version`（不带 `v`，如 `1.9.0`）。也可以推送符合 `v*` 格式的 tag（如 `v1.9.0`）触发发布。手动触发时工作流会创建 `v<version>` tag 对应的 GitHub Release。
5. **等待并检查每个步骤**：工作流使用 Java 17、Gradle 8.11.1，正式 Release 编译固定采用 `-Xmx10g`、关闭 Gradle parallel、单 worker、Kotlin in-process，以避免曾出现的 `compileReleaseKotlin` 内存耗尽。完成签名 APK 构建后，还会校验 APK 内启动图标、发布 GitHub Release，并生成公开更新清单。
6. **核对发布结果**：不能只看 workflow 总状态。确认 `Build signed release APK`、图标校验、`Publish GitHub Release`、`Publish public update manifest` 全部成功；打开 GitHub Releases 检查版本 tag 和 APK 附件，再检查 `updates` 分支的 `latest.json` 中版本、下载 URL、文件大小和 SHA-256 与 Release 附件一致。
7. **安装验证**：下载正式签名 APK，在实际 Android 设备上验证安装、升级覆盖、启动和核心记录流程。CI 的 debug APK 不能替代正式签名包的设备验证。

### 发布失败时的处理规则

- **编译阶段失败**：先查看失败 job 的具体 Gradle task 和完整错误日志。若是 `compileReleaseKotlin` 的 OOM，先检查 10G Gradle JVM、单 worker、Kotlin in-process 配置是否仍在工作流中生效；不要只因耗时长就盲目重跑。
- **Release 已发布，但更新清单失败**：这是一个需要单独处理的部分成功状态。先检查 GitHub Release 是否已经存在、APK 附件是否完整，再检查 `updates` 分支的 `latest.json`。不要不检查就再次运行完整 Release：同一个 tag 可能已经发布，重复构建会浪费时间，也可能遇到已有附件处理问题。优先基于已发布 APK 的真实文件名、大小和 SHA-256 补发 / 修复 `latest.json`，并验证更新源。
- **工作区切换失败**：Release 工作流会临时修改 `gradle.properties` 设置构建内存。更新清单步骤切换到 orphan 的 `update-feed` 分支前，必须先执行 `git restore --source=HEAD -- gradle.properties`，清除构建阶段留下的工作区修改；否则 `git switch --orphan update-feed` 会因本地修改而失败。
- **判断成功标准**：GitHub Release 发布成功不代表更新清单也成功；只有 APK、Release 和 `latest.json` 三部分都核验完成，才算完整发布。若只是清单失败，不要把已成功的 APK 编译和 Release 发布误判为失败。

Release 编译耗时明显长于普通 PR CI 是正常的：它需要重新进行正式签名构建、产物校验和发布。Gradle 缓存可能缩短后续构建，但耗时受依赖缓存、runner 负载和代码变更影响，不承诺固定分钟数。不要为了缩短时间而跳过签名、产物校验或更新清单核对。

## NAS 更新与 App 发布的区别

是否需要更新 NAS，按**实际变更文件和服务端行为**判断，而不是按版本号判断：

| 变更范围 | 需要做什么 |
| --- | --- |
| 仅 Android 客户端（`app/`） | 合并后发布 / 安装新的 Android APK；无需更新 NAS Docker |
| 仅文档、README、CHANGELOG 或 GitHub Actions 工作流 | 通常无需更新 NAS Docker；按需要更新 App 或重新运行对应工作流 |
| `server/` API、数据库、备份逻辑 | 需要重新构建并更新 NAS Docker |
| NAS 网页面板、服务端静态资源或 Docker 配置 | 需要更新 NAS Docker |
| Android + NAS 服务端同时修改 | App 和 NAS Docker 都需要更新，并按兼容性要求安排升级顺序 |

更新 NAS Docker 时必须保留原有数据卷（容器内通常为 `/data`）和原来的 `BABYLOG_JWT_SECRET`。不要删除数据目录，不要在普通升级时重新生成 JWT 密钥。部署步骤、备份恢复和代理配置详见 `docs/FAMILY_SYNC.md`。

## 发布前后核对清单

- [ ] PR 所需 CI 全部通过，变更已合并到 `main`。
- [ ] App 版本号、versionCode、README 和 CHANGELOG 已核对。
- [ ] Release Secrets 有效，触发的是 **BabyLog Android Release**，版本号正确。
- [ ] 正式签名 APK、图标校验和 GitHub Release 发布成功。
- [ ] `updates/latest.json` 已更新，且 SHA-256、大小、下载 URL 与 Release APK 一致。
- [ ] 在实际 Android 设备上验证安装 / 升级和核心功能。
- [ ] 根据改动范围判断是否更新 NAS；需要更新时保留数据卷和 JWT Secret。

## 仓库维护约定

- 代码合并到 `main` 不等于 NAS 容器已更新。
- 服务端或网页面板改动需要重新构建 / 更新 NAS 容器；仅 Android 改动才需要重新安装 APK。
- 普通升级必须保留 NAS 数据卷和已配置的 `BABYLOG_JWT_SECRET`。
- 不应将真实账号、密码、JWT 密钥、家庭数据或照片写入测试样例与仓库。
- 未通过 Android 构建、API 测试或 Docker 构建的变更，不应标记为完成。
