# 预案（If-Then）

一个个人用的 if-then 决策预案安卓 app，实践心理学中的 **implementation intentions**（执行意图）：提前写好「如果遇到 X，就执行 Y」，在诱惑/状态出现的当下不靠临场意志力，直接照案执行。

不是 Tasker 那类手机自动化，也不是 AI 工作流引擎——它管理的是**你和自己订的行为预案**。

## 核心循环

```
写预案 → 触发时一步打卡 → 晚上补记 → 复盘统计 → 修订预案库
```

- **手动打卡**：点卡片 = 这次做到了，长按 = 没做到（诚实记录比连击重要）
- **补记三态**：每条记录是 `做了 / 没做 / 待定`，复盘页可「补记触发」生成待定项，睡前翻流水补齐
- **复盘闭环**：连击（连续顶住次数）、执行率、30 天热力图、最常触发（真实战场）、僵尸预案（两周没触发，建议归档）
- **AI 帮写**：描述困扰 → 模型出 3~5 条候选预案 → 人工勾选确认才入库（AI 不直接污染预案库）

## 界面

| 页面 | 内容 |
|---|---|
| 首页 | 「今天的现场」高频区（按触发统计自动选前 6 条，一步可达）+ 全部预案 |
| 复盘 | 今日流水、热力图、全部战绩、僵尸预案、最常触发、已归档 |
| 新建/编辑 | 如果…那就…、标签、归档/删除、AI 生成候选 |
| 设置 | AI 接口（BYOK）、JSON 导出/导入 |

## 安装

到 [Releases](../../releases) 下载 `app-debug.apk`，手机上安装（允许未知来源）。

要求：Android 8.0+（minSdk 26）。数据全部存本地，AI 功能需要你自备一个 OpenAI 兼容接口（默认配置指向 DeepSeek）。

## 数据与隐私

- 所有预案和打卡记录存在设备本地（Room/SQLite），不经过任何服务器
- AI 生成候选时只把你输入的「困扰描述」发给 API，预案库不出门
- API Key 存 DataStore 私有目录，**不会**进入 JSON 导出文件
- 备份：设置页手动导出/导入 JSON（导入前会二次确认覆盖）

## 构建

```bash
# 需要 JDK 17 + Android SDK 35
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

技术栈：Kotlin · Jetpack Compose · Material 3 · Room · DataStore · OkHttp · kotlinx.serialization

## 已知未验证项

- DeepSeek 真实接口的 AI 生成尚未跑通测试（代码与 UI 已就绪）
- SAF 文件选择器的导出/导入未在真机上手动验证

## 规则阈值

统计口径的调整参数集中在 [`Constants.kt`](app/src/main/java/com/ifthen/app/Constants.kt)：高频区条数、连击窗口、僵尸判定天数（默认 14）、热力图天数，按实际手感改。
