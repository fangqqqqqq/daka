param([string]$Serial = 'emulator-5580', [switch]$ExpectBlocked)
$ErrorActionPreference = 'Stop'
$adb = 'D:/Android_SDK/platform-tools/adb.exe'
function Invoke-Adb { & $adb -s $Serial @args; if ($LASTEXITCODE -ne 0) { throw "adb failed: $args" } }
$fixture = (Invoke-Adb shell dumpsys package com.ss.android.lark) -join "`n"
if ($fixture -notmatch 'versionName=qa-only' -or "$(Invoke-Adb shell getprop ro.boot.qemu.avd_name)".Trim() -ne 'Daka_Test_API35') { throw 'Use the isolated project emulator with the QA receiver, not real Feishu.' }
function Tap-Text([string]$Text) {
    Invoke-Adb shell uiautomator dump /sdcard/daka-ui.xml | Out-Null
    [xml]$ui = (Invoke-Adb shell cat /sdcard/daka-ui.xml) -join "`n"
    $node = $ui.SelectNodes('//node') | Where-Object { $_.text -like $Text } | Select-Object -First 1
    if (!$node) { throw "Button missing: $Text" }
    $b = [regex]::Matches($node.bounds,'\d+') | ForEach-Object { [int]$_.Value }
    Invoke-Adb shell input tap ([int](($b[0]+$b[2])/2)) ([int](($b[1]+$b[3])/2))
}
Invoke-Adb shell am force-stop cn.fangq.daka
$before = (Invoke-Adb shell run-as cn.fangq.daka cat /data/user/0/cn.fangq.daka/shared_prefs/daka.xml) -join "`n"
$previousTask = [regex]::Match($before,'<string name="notification_task">([^<]+)</string>').Groups[1].Value
Invoke-Adb shell am start -W -n cn.fangq.daka/.MainActivity | Out-Null
Tap-Text '任务'
Tap-Text '测试此任务'
if (!$ExpectBlocked) {
    # Changing a setting must not cancel the just-created ten-second test.
    # Master settings are left unchanged; separate settings tests cover edits.
}
Invoke-Adb shell input keyevent KEYCODE_HOME
Start-Sleep -Seconds 13
$timed = (Invoke-Adb shell dumpsys activity activities) -join "`n"
$opened = $timed -match 'topResumedActivity=.*cn.fangq.qa.feishu.TargetActivity'
$prefs = (Invoke-Adb shell run-as cn.fangq.daka cat /data/user/0/cn.fangq.daka/shared_prefs/daka.xml) -join "`n"
$currentTask = [regex]::Match($prefs,'<string name="notification_task">([^<]+)</string>').Groups[1].Value
if (!$currentTask.StartsWith('TEST:') -or $currentTask -eq $previousTask) { throw 'FAIL: a new test alarm never fired; this is not the reported bug' }
if ($prefs -match '<string name="session_id">TEST:') { throw 'FAIL: test opened an automatic-click session' }
Write-Output "taskTestOpen=$opened"
if ($ExpectBlocked) { if ($opened) { throw 'FAIL: expected old-version failure' } }
elseif (!$opened) { throw 'FAIL: scheduled target did not open from background' }


