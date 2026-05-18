param(
    [string]$Adb = 'C:\Users\a1258\AppData\Local\Android\Sdk\platform-tools\adb.exe',
    [string]$Apk = 'C:\Users\a1258\Documents\AI_ANDROID\app\build\outputs\apk\debug\app-debug.apk',
    [string]$Package = 'com.example.aiandroidchat'
)

$ErrorActionPreference = 'Stop'

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    & $Adb @Arguments
}

function Assert-Contains {
    param(
        [string]$Text,
        [string]$Pattern,
        [string]$Message
    )
    if ($Text -notmatch $Pattern) {
        throw $Message
    }
}

function Get-NodeBounds {
    param(
        [string]$Xml,
        [string]$Text
    )
    $pattern = 'text="' + [regex]::Escape($Text) + '".*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    $match = [regex]::Match($Xml, $pattern, 'Singleline')
    if (-not $match.Success) {
        throw "未找到控件：$Text"
    }
    [pscustomobject]@{
        Left = [int]$match.Groups[1].Value
        Top = [int]$match.Groups[2].Value
        Right = [int]$match.Groups[3].Value
        Bottom = [int]$match.Groups[4].Value
    }
}

function Tap-Center {
    param([pscustomobject]$Bounds)
    $x = [int](($Bounds.Left + $Bounds.Right) / 2)
    $y = [int](($Bounds.Top + $Bounds.Bottom) / 2)
    Invoke-Adb @('shell', 'input', 'tap', "$x", "$y") | Out-Null
}

function Start-App {
    Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null
    Invoke-Adb @('shell', 'wm', 'dismiss-keyguard') | Out-Null
    Invoke-Adb @('shell', 'cmd', 'statusbar', 'collapse') | Out-Null
    Start-Sleep -Milliseconds 300
    Invoke-Adb @('shell', 'am', 'start', '-W', '--user', '0', '-n', "$Package/.MainActivity") | Out-Host
    Invoke-Adb @('shell', 'cmd', 'statusbar', 'collapse') | Out-Null
    Start-Sleep -Milliseconds 300
    $focus = Invoke-Adb @('shell', 'dumpsys', 'window') | Out-String
    Assert-Contains $focus ([regex]::Escape("$Package/.MainActivity")) '前台焦点不是目标 Activity'
}

Write-Host '1/7 检查设备和用户...'
$devices = Invoke-Adb @('devices', '-l') | Out-String
Assert-Contains $devices '\bdevice\b' '没有可用 adb 设备'
$users = Invoke-Adb @('shell', 'pm', 'list', 'users') | Out-String
Assert-Contains $users 'UserInfo\{0:' '未检测到机主用户 0'

Write-Host '2/7 安装到机主用户 0...'
Invoke-Adb @('install', '-r', '--user', '0', $Apk) | Out-Host
$user0 = Invoke-Adb @('shell', 'pm', 'list', 'packages', '--user', '0') | Out-String
Assert-Contains $user0 ([regex]::Escape($Package)) '用户 0 未安装目标包'
if ($users -match 'UserInfo\{900:') {
    $user900 = Invoke-Adb @('shell', 'pm', 'list', 'packages', '--user', '900') | Out-String
    if ($user900 -match [regex]::Escape($Package)) {
        throw '应用分身用户 900 存在重复安装'
    }
}

Write-Host '3/7 启动 App...'
Invoke-Adb @('shell', 'am', 'force-stop', $Package) | Out-Null
Start-App

Write-Host '4/7 验证键盘不遮挡输入框...'
Start-App
Invoke-Adb @('shell', 'input', 'tap', '620', '200') | Out-Null
Start-Sleep -Milliseconds 800
Invoke-Adb @('shell', 'input', 'tap', '220', '2235') | Out-Null
Start-Sleep -Milliseconds 500
Invoke-Adb @('shell', 'input', 'tap', '220', '2235') | Out-Null
Start-Sleep -Seconds 1
$ime = Invoke-Adb @('shell', 'dumpsys', 'input_method') | Out-String
Assert-Contains $ime 'mInputShown=true' '输入法没有弹出，无法验证遮挡'
Invoke-Adb @('shell', 'uiautomator', 'dump', '/sdcard/window_regression.xml') | Out-Null
$xml = Invoke-Adb @('exec-out', 'cat', '/sdcard/window_regression.xml') | Out-String
$inputBounds = Get-NodeBounds $xml '输入消息'
$cameraBounds = Get-NodeBounds $xml '拍照'
$sheetMusicBounds = Get-NodeBounds $xml '乐谱'
$sendBounds = Get-NodeBounds $xml '发送'

# On this 1080x2388 device, a covered composer remains near the bottom
# around y=2200. A visible composer above the Sogou IME sits around y=1280.
$safeBottom = 1700
if ($inputBounds.Bottom -gt $safeBottom -or $cameraBounds.Bottom -gt $safeBottom -or $sheetMusicBounds.Bottom -gt $safeBottom -or $sendBounds.Bottom -gt $safeBottom) {
    throw "输入栏仍可能被键盘遮挡：inputBottom=$($inputBounds.Bottom), cameraBottom=$($cameraBounds.Bottom), sheetMusicBottom=$($sheetMusicBounds.Bottom), sendBottom=$($sendBounds.Bottom)"
}
Write-Host "键盘回归通过：inputBottom=$($inputBounds.Bottom), cameraBottom=$($cameraBounds.Bottom), sheetMusicBottom=$($sheetMusicBounds.Bottom), sendBottom=$($sendBounds.Bottom)"

Write-Host '5/7 验证模型选择不锁住输入区...'
Start-App
Invoke-Adb @('shell', 'input', 'tap', '960', '200') | Out-Null
Start-Sleep -Milliseconds 500
Invoke-Adb @('shell', 'uiautomator', 'dump', '/sdcard/window_account.xml') | Out-Null
$accountXml = Invoke-Adb @('exec-out', 'cat', '/sdcard/window_account.xml') | Out-String
$modelButtonBounds = Get-NodeBounds $accountXml '模型选择'
Tap-Center $modelButtonBounds
Start-Sleep -Milliseconds 500
Invoke-Adb @('shell', 'uiautomator', 'dump', '/sdcard/window_model_loading.xml') | Out-Null
$modelXml = Invoke-Adb @('exec-out', 'cat', '/sdcard/window_model_loading.xml') | Out-String
Assert-Contains $modelXml '正在读取模型列表|使用默认列表|gpt-5' '模型选择加载或选项弹窗没有出现'
Invoke-Adb @('shell', 'input', 'keyevent', 'BACK') | Out-Null
Start-Sleep -Milliseconds 300
Invoke-Adb @('shell', 'input', 'keyevent', 'BACK') | Out-Null
Start-Sleep -Milliseconds 500
Invoke-Adb @('shell', 'uiautomator', 'dump', '/sdcard/window_after_model_cancel.xml') | Out-Null
$afterModelXml = Invoke-Adb @('exec-out', 'cat', '/sdcard/window_after_model_cancel.xml') | Out-String
Assert-Contains $afterModelXml 'text="拍照"[^>]*enabled="true"' '取消模型选择后拍照按钮仍不可用'
Assert-Contains $afterModelXml 'text="发送"[^>]*enabled="true"' '取消模型选择后发送按钮仍不可用'
if ($afterModelXml -match '正在读取模型列表') {
    throw '取消模型选择后仍显示全局模型加载状态'
}

Write-Host '6/7 验证新建和历史入口...'
Start-App
Invoke-Adb @('shell', 'input', 'tap', '620', '200') | Out-Null
Start-Sleep -Milliseconds 800
Invoke-Adb @('shell', 'uiautomator', 'dump', '/sdcard/window_new.xml') | Out-Null
$newXml = Invoke-Adb @('exec-out', 'cat', '/sdcard/window_new.xml') | Out-String
Assert-Contains $newXml '开始一段对话吧' '新建对话后没有空状态'
Invoke-Adb @('shell', 'input', 'tap', '790', '200') | Out-Null
Start-Sleep -Milliseconds 800
Invoke-Adb @('shell', 'uiautomator', 'dump', '/sdcard/window_history.xml') | Out-Null
$historyXml = Invoke-Adb @('exec-out', 'cat', '/sdcard/window_history.xml') | Out-String
Assert-Contains $historyXml '历史对话' '历史弹窗没有出现'
Invoke-Adb @('shell', 'input', 'keyevent', 'BACK') | Out-Null

Write-Host '7/7 检查崩溃日志...'
$logs = Invoke-Adb @('shell', 'logcat', '-d', '-t', '500') | Out-String
if ($logs -match 'FATAL EXCEPTION' -and $logs -match [regex]::Escape($Package)) {
    throw '发现目标 App 崩溃日志'
}

Write-Host 'Android 回归测试通过'
