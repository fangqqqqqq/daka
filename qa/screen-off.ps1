param([string]$Serial='emulator-5580')
$ErrorActionPreference='Stop'
$adb='D:/Android_SDK/platform-tools/adb.exe'
function Adb { $output=& $adb -s $Serial @args; if($LASTEXITCODE -ne 0) { throw "adb failed: $args" }; return $output }
function ReadQaHistory { return (Adb shell run-as com.ss.android.lark cat /data/user/0/com.ss.android.lark/shared_prefs/qa.xml) -join "`n" }
$fixture=(Adb shell dumpsys package com.ss.android.lark) -join "`n"
if($fixture -notmatch 'versionName=qa-only' -or "$(Adb shell getprop ro.boot.qemu.avd_name)".Trim() -ne 'Daka_Test_API35') { throw 'Only run on the isolated project emulator with QA Feishu.' }
$policy=(Adb shell dumpsys window policy) -join "`n"
if($policy -notmatch '(?m)^\s+secure=false\s*$') { throw 'Test requires an emulator without an existing screen credential.' }
$originalIdle="$(Adb shell settings get global device_idle_constants)".Trim()
$prepared=$false; $pinSet=$false
try {
    Adb shell input keyevent KEYCODE_WAKEUP
    Adb shell wm dismiss-keyguard
    Adb shell am start -W -n com.ss.android.lark/cn.fangq.qa.feishu.TargetActivity | Out-Null
    $before=[regex]::Matches((ReadQaHistory),'qa_alpha').Count
    $setPin=(Adb shell locksettings set-pin 1357) -join "`n"
    if($setPin -notmatch 'PIN set') { throw $setPin }
    $pinSet=$true
    # Permit forced deep idle even with this imminent test alarm. Restore in finally.
    $idleOverride=if($originalIdle -eq 'null' -or !$originalIdle) { 'min_time_to_alarm=0' } else { "$originalIdle,min_time_to_alarm=0" }
    Adb shell settings put global device_idle_constants $idleOverride
    $output=(Adb shell am instrument -w -e scenario screen cn.fangq.daka.test/cn.fangq.daka.SettingsInstrumentation) -join "`n"
    if($output -notmatch 'PASS prepared') { throw $output }
    $prepared=$true
    Adb shell input keyevent KEYCODE_HOME
    Adb shell dumpsys battery unplug
    Adb shell input keyevent KEYCODE_SLEEP
    Adb shell cmd deviceidle force-idle | Out-Null
    $idle=(Adb shell dumpsys deviceidle) -join "`n"
    if($idle -notmatch 'mState=IDLE') { throw 'Device did not enter deep idle before the alarm.' }
    Write-Output 'Verified: screen off, PIN lock, deep idle before trigger.'
    Start-Sleep -Seconds 27
    $audio=(Adb shell dumpsys audio) -join "`n"
    if($audio -notmatch 'state:started.*USAGE_ALARM') { throw 'Alarm audio was not playing after the screen-off trigger.' }
    $activities=(Adb shell dumpsys activity activities) -join "`n"
    if($activities -notmatch 'topResumedActivity=.*cn\.fangq\.daka/(\.|cn\.fangq\.daka\.)AlarmActivity') { throw 'Lock-screen reminder did not become foreground.' }
    if([regex]::Matches((ReadQaHistory),'qa_alpha').Count -ne $before) { throw 'Target opened before the PIN lock was dismissed.' }
    Write-Output 'PASS: alarm audio and full-screen reminder while locked; no premature target launch.'
    Start-Sleep -Seconds 17
    $endedAudio=(Adb shell dumpsys audio) -join "`n"
    if($endedAudio -match 'state:started.*USAGE_ALARM') { throw 'Alarm audio kept playing past its selected duration.' }
    $power=(Adb shell dumpsys power) -join "`n"
    if($power -notmatch "PARTIAL_WAKE_LOCK[^\r\n]*'daka:alarm'") { throw 'Wake lock ended with the ringtone before the user could unlock.' }
    Write-Output 'PASS: unlock waiting window remains awake after sound duration ends.'
    # Clear only the temporary test PIN, then simulate the dismissed keyguard.
    Adb shell locksettings clear --old 1357 | Out-Null
    $pinSet=$false
    Adb shell wm dismiss-keyguard
    Start-Sleep -Seconds 4
    if([regex]::Matches((ReadQaHistory),'qa_alpha').Count -le $before) { throw 'Target did not open after unlock.' }
    $windows=(Adb shell dumpsys window windows) -join "`n"
    if($windows -notmatch 'ty=APPLICATION_OVERLAY') { throw 'Reminder overlay did not remain above the target.' }
    Adb shell screencap -p /sdcard/daka-overlay.png
    Adb pull /sdcard/daka-overlay.png (Join-Path $PSScriptRoot '../.tools/ui-overlay-v0.2.1.png') | Out-Null
    Write-Output 'PASS: deferred target launch after unlock with retained top overlay.'
} finally {
    Adb shell cmd deviceidle unforce | Out-Null
    Adb shell dumpsys battery reset
    if($originalIdle -eq 'null' -or !$originalIdle) { Adb shell settings delete global device_idle_constants | Out-Null } else { Adb shell settings put global device_idle_constants $originalIdle }
    if($pinSet) { Adb shell locksettings clear --old 1357 | Out-Null }
    Adb shell input keyevent KEYCODE_WAKEUP
    Adb shell wm dismiss-keyguard
    if($prepared) {
        $output=(Adb shell am instrument -w -e scenario restore cn.fangq.daka.test/cn.fangq.daka.SettingsInstrumentation) -join "`n"
        if($output -notmatch 'PASS restored') { throw $output }
    }
    Write-Output 'Original task settings, idle configuration, battery and screen credential restored.'
}
