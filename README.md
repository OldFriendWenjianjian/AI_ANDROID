# AI Android Chat

一个原生 Android AI 对话应用：

- 使用 Smart Human 账号注册/登录。
- 登录后自动创建/使用网关 API Key。
- 通过 OpenAI 兼容网关调用 AI。
- 支持多段历史对话、新建对话，本机保存到 `SharedPreferences`。
- 可在应用内从账号可用模型列表里选择模型。
- 输入法弹出时会把输入栏顶到键盘上方。
- 发送消息后先探测服务器连通性，立即显示等待提示和横向进度条，再等待 AI 生成回复。

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
- 最近日志里没有目标 App 崩溃。
