# 开发与 CI 约定

BabyLog Android 客户端、NAS 同步服务和网页管理面板属于同一个产品版本，必须一起维护、一起验证。

## 主分支 CI

向 `main` 推送或创建 PR 时，CI 并行执行：

1. **Android 单元测试与构建**：运行 JVM / Robolectric 测试并构建 debug APK，覆盖可在 JVM 环境验证的 Room、统计逻辑、同步队列及加密相关逻辑。
2. **Sync API**：运行 FastAPI 接口测试，覆盖认证、家庭数据访问、增量同步、幂等、照片上传下载、网页面板、备份导出与安全导入。
3. **Docker**：构建 NAS 同步服务镜像，执行容器健康检查并校验 Compose 配置。

以上 Android、NAS API 与 Docker 检查全部通过，才视为该提交通过 CI。当前 CI 不包含 Android Emulator / Compose UI 自动化；视觉布局、系统返回、手势、键盘遮挡、TalkBack 和正式签名 APK 安装/覆盖升级仍需在目标设备上验证。Android APK 作为 Actions artifact 上传；Docker 镜像由 CI 构建验证，不代表 NAS 已自动更新。合并代码后仍需由部署者在 NAS / Dockge 更新容器。

## Android UI 架构与约定

- `ui/theme/Theme.kt` 统一维护默认暖色、卡皮巴拉与考拉三套 Material 3 配色、主题偏好和 Typography；`ThemeDecorations.kt` 统一维护主题角色插画和页面横幅，主题插画使用本地矢量资源，不依赖网络。页面颜色优先使用 `MaterialTheme.colorScheme` 的语义角色，不在普通页面散落硬编码颜色。
- 外观主题偏好只保存在 Android 本机 SharedPreferences，不进入 Room、备份或家庭同步；切换主题时同步切换对应的 Launcher activity-alias 图标。
- `navigation/` 维护首页、记录、成长、相册、疫苗五个一级目的地；设置通过首页入口独立打开，不占用底部导航位置。
- `BabyProfileComponents.kt`、`EventEditor.kt`、`DateTimePickerDialog.kt`、`FormSelectionRow.kt` 与 `TimelineComponents.kt` 承载可复用的档案、表单、日期和时间线 UI。
- `MainActivity` 负责组合 Room 数据流、页面导航和现有同步操作；新的完整表单或可复用展示组件应优先放到独立文件，避免继续堆积在 Activity 中。
- `BabyLogTheme` 必须同时覆盖主页面与其同级弹窗 / ModalBottomSheet，确保弹层沿用同一套颜色和字体。
- Activity 使用 `adjustResize`；可滚动编辑器和设置弹窗应结合 `imePadding()` 与滚动容器，避免软键盘遮住输入项。
- UI 整理不得顺带改变 Room Entity、DAO、数据库版本 / Migration、事件 payload、照片路径、同步队列或 NAS API 契约。

## BabyLog 3.0 数据与 UI 演进约定

- Room Schema 由 KSP 导出到 `app/schemas`，每次 Android CI 同时上传 Schema artifact；数据库版本或 Entity 发生变化时，必须将生成的快照提交到仓库。
- 3.0 首页、记录编辑器、时间线和统计页面共享同一套事件解释规则；展示层可以新增 UI model，但不得把展示字段写回 `BabyEventEntity.payloadJson` 作为重复数据。
- 现有 `BabyEventEntity` 的 ID、babyId、eventType、occurredAt、payloadJson、createdBy、updatedAt、syncStatus、serverVersion、deletedAt 均视为兼容边界；未经过单独迁移设计和同步契约验证，不得重命名、删除或改变语义。
- 时间线继续使用 `occurredAt + id` 的稳定排序与 keyset 分页；首页今日摘要只查询当前日期范围，不得为了绘制首页而加载全部历史事件。
- 计时中的睡眠状态与已完成的睡眠事件分开处理；只有用户结束并保存后，才创建正式事件并进入同步队列。
- 阶段性重构优先拆分 Compose 展示组件和查询/映射逻辑，不要求一次性迁移到新的架构框架，也不改变离线优先、照片本地路径、NAS API 和同步冲突处理行为。

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

## 历史发布准备说明

3.0.0 阶段的发布候选检查已属于历史记录；当前版本发布以本文件下方的通用 Android 正式发布流程、当前 PR 验收结果和维护者确认作为准则。

## Android 正式发布流程

正式签名 APK 发布由 `.github/workflows/release.yml`（GitHub Actions：**BabyLog Android Release**）负责。它与 PR / main 的日常 CI 是两套独立流程：

1. **先完成代码整合**：所有待发布变更先通过 PR 的 Android、NAS API、NAS Docker 三项检查并合并到 `main`。不要把 PR 的 debug APK 当作正式发布包。
2. **核对版本与发布内容**：检查 `CHANGELOG.md`、README 和用户可见更新说明。发布工作流使用输入的版本名覆盖 Gradle 默认值；`versionCode` 根据 GitHub Actions run number 生成。不要在多个地方手工填写不一致的版本号。
3. **确认签名 Secrets**：仓库 Actions Secrets 必须配置 `BABYLOG_KEYSTORE_BASE64`、`BABYLOG_KEYSTORE_PASSWORD`、`BABYLOG_KEY_ALIAS`、`BABYLOG_KEY_PASSWORD`。工作流会验证四项并解码 PKCS#12 keystore。签名密钥必须长期妥善备份，不能提交到仓库。密钥生成和配置见 [RELEASE_SIGNING.md](RELEASE_SIGNING.md)。
4. **触发 Release**：推荐在 Actions 中选择 **BabyLog Android Release**，点击 **Run workflow**，明确选择 `main`，填写 `version`（不带 `v`；BabyLog 3.0.0 正式发布时填写 `3.0.0`）。也可以推送 `v*` 格式的 tag 触发发布；推送 tag 前必须确认它指向已合并并通过 CI 的提交。手动触发会使用输入版本创建对应的 `v<version>` tag。
5. **等待并检查工作流步骤**：Release 会执行签名 APK 构建、APK 启动图标资源校验、GitHub Release 发布和公开更新清单发布。构建会临时提高 Gradle JVM 堆内存、关闭并行构建、限制单 Worker，并使用 Kotlin in-process 编译，以降低曾出现的 Kotlin 编译内存耗尽问题。不要因耗时较长而提前判定失败。
6. **分别核验三类发布产物**：确认 `Build signed release APK`、图标校验、`Publish GitHub Release`、`Publish public update manifest` 全部成功；打开 GitHub Releases 检查 tag 和 APK 附件，再检查 `updates` 分支的 `latest.json` 中版本、下载 URL、文件大小和 SHA-256 与实际 Release APK 一致。
7. **真实设备验证**：下载正式签名 APK，在 Android 设备上验证全新安装、覆盖升级、启动和核心记录流程。CI 的 debug APK 不能替代正式签名包的设备验证。

### 发布失败时的处理规则

- **编译阶段失败**：先查看失败 job 的具体 Gradle task 和完整错误日志。若是 Kotlin 编译 OOM，先核对 10G Gradle JVM、单 Worker、关闭并行和 Kotlin in-process 配置是否仍在工作流中生效；不要只因耗时长就盲目重跑。
- **Release 已发布，但更新清单失败**：这是部分成功，不应把已成功的 APK 编译和 GitHub Release 误判为失败。先检查 Release 是否存在、APK 附件是否完整，再检查 `updates/latest.json`。不要未经检查就重复运行完整 Release；同一个 tag 可能已发布。优先基于已发布 APK 的真实文件名、大小和 SHA-256 修复更新清单，并验证更新源。
- **切换更新分支失败**：工作流会临时修改 `gradle.properties` 设置构建内存。切换到 orphan 的 `update-feed` 分支前必须先执行 `git restore --source=HEAD -- gradle.properties`，清除构建阶段留下的工作区修改；否则分支切换可能因本地修改而失败。
- **成功标准**：APK 构建、GitHub Release 和 `updates/latest.json` 三部分均核验完成，才算完整发布。仅 Release 成功不代表应用内更新源也已更新。

正式 Release 构建耗时通常明显长于普通 PR CI：它需要重新进行签名构建、产物校验和发布。耗时受 runner 负载、依赖缓存和代码变更影响，不承诺固定分钟数。不要为了缩短时间而跳过签名、产物校验或更新清单核对。

## NAS 更新与 App 发布的区别

是否需要更新 NAS，按**实际变更文件和服务端行为**判断，而不是按版本号判断：

| 变更范围 | 需要做什么 |
| --- | --- |
| 仅 Android 客户端（`app/`） | 合并后发布 / 安装新的 Android APK；无需更新 NAS Docker |
| 仅文档、README、CHANGELOG 或 GitHub Actions 工作流 | 通常无需更新 NAS Docker；按需要更新 App 或重新运行对应工作流 |
| `server/` API、数据库、备份逻辑 | 需要重新构建并更新 NAS Docker |
| NAS 网页面板、服务端静态资源或 Docker 配置 | 需要更新 NAS Docker |
| Android + NAS 服务端同时修改 | App 和 NAS Docker 都需要更新，并按兼容性要求安排升级顺序 |

更新 NAS Docker 时必须保留原有数据卷（容器内通常为 `/data`）和原来的 `BABYLOG_JWT_SECRET`。不要删除数据目录，不要在普通升级时重新生成 JWT 密钥。部署步骤、备份恢复和代理配置详见 [FAMILY_SYNC.md](FAMILY_SYNC.md)。

## 发布前后核对清单

- [ ] PR 所需 CI 全部通过，变更已合并到 `main`。
- [ ] App 版本名、自动生成的 versionCode、README 和 CHANGELOG 已核对。
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
