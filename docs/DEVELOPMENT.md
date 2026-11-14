# 开发说明

## 环境
- Android Studio（建议使用当前稳定版）
- JDK 17
- Android SDK 35
- Gradle 8.11.1（CI 使用指定版本）

## 本地运行
1. 克隆仓库并用 Android Studio 打开。
2. 等待 Gradle Sync。
3. 连接 Android 设备或启动模拟器。
4. 运行 app 配置。

当前首版 UI 仅为工程骨架，按钮尚未接入持久化操作；请勿将其视为可用记录工具。

## Supabase
在 Supabase 项目 SQL Editor 中按顺序执行 `supabase/migrations/` 下迁移文件。客户端只允许使用 publishable/anon key；绝不把 service_role key 放进 Android 包或 Git。正式启用前需要完成家庭创建/邀请 RPC、RLS 自动化测试和同步冲突策略。

## 质量门禁
每次提交由 GitHub Actions 执行 Debug APK 构建。后续加入单元测试、Room migration 测试及 Supabase RLS 测试。
