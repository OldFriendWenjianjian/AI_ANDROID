# AI Android Chat

一个原生 Android AI 对话应用：

- 使用 Smart Human 账号注册/登录。
- 登录后自动创建/使用网关 API Key。
- 通过 OpenAI 兼容网关调用 AI。
- 支持多段历史对话、新建对话，本机保存到 `SharedPreferences`。
- 可在应用内从账号可用模型列表里选择模型。
- 文生图请求会自动调用 `gpt-image-2`，不用把图片模型手动设成默认对话模型。
- AI 自动提示词已增强：默认用结构化中文回答，复杂问题按结论、步骤、注意事项组织，代码/图片/乐谱等场景会保留关键细节并标注不确定处。
- 助手回复支持基础 Markdown 渲染：标题、加粗、列表、编号、行内代码和代码块会在聊天气泡里更清晰显示。
- 支持真机拍照识别图片：拍完先暂存在输入区，用户确认后点“发送”，聊天记录里显示缩略图。
- 支持乐谱扫描入口：拍完乐谱后先暂存在输入区，默认按系统、小节、音符、节奏、和弦、TAB 和 MusicXML 草稿方向识别，并标注看不清的“待确认”位置。
- 输入法弹出时会把输入栏顶到键盘上方。
- 发送消息后先探测服务器连通性，立即显示等待提示和横向进度条，再等待 AI 生成回复。
- 模型选择使用独立弹窗加载，接口慢时自动回退默认模型列表，不会锁住聊天输入区。

## 配置

在项目根目录创建 `local.properties`：

```properties
sdk.dir=C:\\Users\\a1258\\AppData\\Local\\Android\\Sdk
```

应用内保存的登录 token 和网关 API Key 仅保存在当前设备本地。

## 构建

```powershell
.\gradlew.bat assembleDebug
```

Debug APK 输出位置：

```text
app\build\outputs\apk\debug\app-debug.apk
```

## 真机回归

每次改 UI 或安装真机后运行：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\android_regression.ps1
```

回归脚本会检查：

- 只安装在机主用户 `0`，应用分身用户 `900` 没有重复副本。
- App 能直接启动到 `MainActivity`。
- 输入法弹出时，输入框和发送按钮必须在键盘上方。
- 新建对话和历史入口可用。
- 拍照、乐谱按钮在键盘弹出时不被遮挡。
- 模型选择弹窗不会让主界面长期处于不可输入状态。
- 最近日志里没有目标 App 崩溃。
