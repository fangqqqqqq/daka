# 隔离模拟器回归检查

0.2.2 默认设备序列号为 `emulator-5580`。首页快速验证已移除，单任务测试继续保留。界面检查覆盖自定义任务与自动保存；使用 `custom-tasks.ps1` 验证两个自定义任务同时从后台打开独立入口：

```powershell
.\qa\custom-tasks.ps1 -Serial emulator-5580
.\qa\rearm-task.ps1 -Serial emulator-5580
.\qa\screen-off.ps1 -Serial emulator-5580
```

仅在项目专属模拟器执行；脚本确认目标为 `qa-only` 接收器，并在结束后恢复已有任务与提醒设置。首次触发前记录接收器历史数量，要求两个 URI 均新增，避免把历史跳转误判为本次成功。

`rearm-task.ps1` 验证第一次正常触发、完成后改时间、同一天第二次触发及更新入口，并检查响铃时长结束后仍保留应用悬浮窗。`screen-off.ps1` 只允许没有密码的专属模拟器，临时设置测试 PIN，熄屏并进入深度待机；要求闹钟音频正在播放、锁屏提醒 Activity 在前台、密码锁解除前不能打开目标。响铃结束后检查唤醒锁仍在等待窗口持有，移除临时测试 PIN 并解除 keyguard 后要求跳转且保留浮窗。finally 恢复任务、提醒偏好、深度待机参数、电池模拟状态和原来的无密码配置。

音频检查使用设备 `dumpsys audio` 的播放状态，不以主机听到声音作为证据。模拟器不能覆盖一加厂商后台管理，真机可设置一项临近的正常任务后息屏验证。

`feishu-stub` 是离线跳转接收器，使用与官方飞书相同的 Android 包名，仅用于没有安装真实飞书的项目专属模拟器。它不包含飞书 SDK、不登录、不联网，也不提交日报或考勤。不要安装到日常手机；它不是随正式 APK 交付的组件。

准备 Android 15 模拟器，安装助手和接收器，授权助手通知、精准闹钟、全屏提醒、悬浮提醒，并解锁屏幕。

```powershell
# 使用项目的 JDK 17 / SDK 35 配置
.\gradlew.bat -PwithFeishuStub :feishu-stub:assembleDebug --no-daemon
adb -s emulator-5580 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5580 install -r qa/feishu-stub/build/outputs/apk/debug/feishu-stub-debug.apk
.\qa\timed-open.ps1
```

脚本从实际任务页的“测试此任务”按钮出发，退到桌面，检查十秒后接收器是否成为前台 Activity。至少需要一项已配置任务，且任务执行方式为自动打开。`-ExpectBlocked` 仅用于旧版本故障复现；默认检查失败返回非零退出码。

测试应选择第二种自动模式。第三种模式的定时测试也只能打开页面，不能创建自动点击会话；第一种模式只提醒，需要手动打开。

设置集成测试通过真实控件监听器验证自动保存、重建页面、有效值与草稿分离、开关改变后的实际调度。仅在专属模拟器执行，会修改测试设备的助手配置：

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest --no-daemon
adb -s emulator-5580 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5580 shell am instrument -w cn.fangq.daka.test/cn.fangq.daka.SettingsInstrumentation
```

安装真实飞书前，在这个专属模拟器卸载接收器：`adb -s emulator-5580 uninstall com.ss.android.lark`。真实飞书使用飞书官方安装包并由用户本人登录。模拟器可验证日报跳转，但一加后台策略和实际拍照仍需真机验证。
