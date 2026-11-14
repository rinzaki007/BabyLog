# BabyLog 家庭共享与双设备同步

## 目标

两位家长分别使用自己的账号登录同一个家庭空间。每台 Android 设备继续使用 Room 本地数据库，记录不依赖实时网络；WorkManager 在网络恢复后上传本地变更、拉取另一台设备的变更。

## 数据流

1. Room 是设备端的本地读写入口（offline-first）。
2. 新增、编辑、删除操作写入本地，并将 syncStatus 标记为 PENDING。
3. WorkManager 在有网络时按家庭同步宝宝档案、日常记录、成长测量和照片元数据。
4. Supabase Auth 负责个人账号；family_members 负责家庭成员关系。
5. PostgreSQL RLS 确保账号只能读取自己所属家庭的数据。
6. Supabase Storage 的 baby-album 私有 Bucket 保存照片原图；数据库只保存 storage_path。

## 家庭加入流程

- 家长 A 注册/登录后创建家庭空间。
- 家长 A 输入家长 B 的注册邮箱，生成限时邀请。
- 邀请码只显示一次，由家长 A 通过私密渠道发送给家长 B。
- 家长 B 使用受邀邮箱登录，在 App 中输入邀请码加入家庭。
- 两台设备都选择同一个家庭后，开始双向同步。

邀请码默认有效 72 小时，数据库只保存邀请码 SHA-256 摘要；邀请码只允许受邀邮箱对应的登录账号使用。

## 冲突与删除

- 每条记录使用 UUID，避免两台设备离线新增时主键冲突。
- 删除采用软删除，删除标记也参与同步，防止另一台设备把已删除记录重新上传。
- 同一条记录在两台设备被同时编辑时，不静默覆盖：按 server version 检测冲突，保留服务端版本并提示用户选择；实现阶段增加冲突记录表和解决界面。
- 服务器时间作为同步游标，客户端仍保留 occurred_at 作为实际发生时间。

## 照片策略

- 本地保存原图，Room 只保存文件路径、拍摄/记录时间、日龄和说明。
- 上传到私有 Storage Bucket，路径格式为 `<family_id>/<baby_id>/<photo_id>.<ext>`。
- 数据库保存 Storage 对象路径，不保存公开 URL；查看时使用已认证下载或短时 signed URL。
- 相册缩略图单独生成，列表只加载缩略图；点开再加载原图。
- 卸载 App 前应提供云端备份状态提示；本地照片不能因为清理缓存而误删。

## Supabase 初始化

1. 创建 Supabase 项目，选择离家长较近且访问稳定的区域。
2. 在 SQL Editor 执行本仓库的 `supabase/schema.sql`。
3. 在 Authentication 中启用 Email 登录；按需要配置邮件验证。
4. 在 Android App 中配置 Supabase Project URL 和 publishable/anon key。它们属于客户端公开配置，不是 service_role 密钥。
5. **绝不**将 service_role key 放进 APK、Git 仓库或客户端配置。
6. 先用两台测试设备验证：账号登录、创建家庭、邀请加入、同一记录双向同步、离线新增后恢复网络同步、照片上传与访问、成员隔离。

## Android 实现阶段

- 阶段 A：本地成长曲线与相册（当前进行中）。
- 阶段 B：Supabase Auth、家庭创建/邀请码加入、家庭切换。
- 阶段 C：WorkManager 双向同步、软删除与冲突检测。
- 阶段 D：照片上传/下载、缩略图、同步状态和失败重试。
- 阶段 E：两台真机验收后再发布带家庭共享的版本。

在 Supabase 项目 URL 和 publishable/anon key 尚未配置前，App 保持本地可用，不会假装云同步已启用。
