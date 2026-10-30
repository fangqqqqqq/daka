param([string]$Serial='emulator-5580')
$ErrorActionPreference='Stop'
$adb='D:/Android_SDK/platform-tools/adb.exe'
function Adb { $output=& $adb -s $Serial @args; if($LASTEXITCODE -ne 0) { throw "adb failed: $args" }; return $output }
function ReadQaHistory { return (Adb shell run-as com.ss.android.lark cat /data/user/0/com.ss.android.lark/shared_prefs/qa.xml) -join "`n" }
$fixture=(Adb shell dumpsys package com.ss.android.lark) -join "`n"
if($fixture -notmatch 'versionName=qa-only' -or "$(Adb shell getprop ro.boot.qemu.avd_name)".Trim() -ne 'Daka_Test_API35') { throw 'Only run on the isolated project emulator with QA Feishu.' }
$prepared=$false
try {
    Adb shell input keyevent KEYCODE_WAKEUP
    Adb shell wm dismiss-keyguard
    Adb shell am start -W -n com.ss.android.lark/cn.fangq.qa.feishu.TargetActivity | Out-Null
    $firstCount=[regex]::Matches((ReadQaHistory),'qa_alpha').Count
    $secondCount=[regex]::Matches((ReadQaHistory),'qa_repeat_second').Count
    $output=(Adb shell am instrument -w -e scenario repeat cn.fangq.daka.test/cn.fangq.daka.SettingsInstrumentation) -join "`n"
    if($output -notmatch 'PASS prepared') { throw $output }
    $prepared=$true
    Adb shell input keyevent KEYCODE_HOME
    Start-Sleep -Seconds 28
    if([regex]::Matches((ReadQaHistory),'qa_alpha').Count -le $firstCount) { throw 'First occurrence did not open its entry.' }
    $windows=(Adb shell dumpsys window windows) -join "`n"
    if($windows -notmatch 'ty=APPLICATION_OVERLAY') { throw 'Overlay was removed after opening the target.' }
    Write-Output 'PASS: first occurrence opened; overlay remains after the 15-second ring window.'
    $output=(Adb shell am instrument -w -e scenario rearm cn.fangq.daka.test/cn.fangq.daka.SettingsInstrumentation) -join "`n"
    if($output -notmatch 'PASS completed') { throw $output }
    Adb shell input keyevent KEYCODE_HOME
    Start-Sleep -Seconds 18
    if([regex]::Matches((ReadQaHistory),'qa_repeat_second').Count -le $secondCount) { throw 'Rearmed occurrence did not open its updated entry.' }
    Write-Output 'PASS: completed task rearmed on the same day and opened its second entry.'
} finally {
    if($prepared) {
        $output=(Adb shell am instrument -w -e scenario restore cn.fangq.daka.test/cn.fangq.daka.SettingsInstrumentation) -join "`n"
        if($output -notmatch 'PASS restored') { throw $output }
        Write-Output 'Original tasks and reminder preferences restored.'
    }
}
