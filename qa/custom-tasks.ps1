param([string]$Serial='emulator-5580')
$ErrorActionPreference='Stop'
$adb='D:/Android_SDK/platform-tools/adb.exe'
function Adb { $output=& $adb -s $Serial @args; if($LASTEXITCODE -ne 0) { throw "adb failed: $args" }; return $output }
$fixture=(Adb shell dumpsys package com.ss.android.lark) -join "`n"
if($fixture -notmatch 'versionName=qa-only') { throw 'Use an isolated emulator with the QA receiver, not real Feishu.' }
Adb shell am start -W -n com.ss.android.lark/cn.fangq.qa.feishu.TargetActivity | Out-Null
$before=(Adb shell run-as com.ss.android.lark cat /data/user/0/com.ss.android.lark/shared_prefs/qa.xml) -join "`n"
$beforeAlpha=[regex]::Matches($before,'qa_alpha').Count
$beforeBeta=[regex]::Matches($before,'qa_beta').Count
$prepared=$false
try {
    $output=(Adb shell am instrument -w -e scenario prepare cn.fangq.daka.test/cn.fangq.daka.SettingsInstrumentation) -join "`n"
    if($output -notmatch 'PASS prepared') { throw $output }
    $prepared=$true
    Adb shell input keyevent KEYCODE_HOME
    Start-Sleep -Seconds 32
    $history=(Adb shell run-as com.ss.android.lark cat /data/user/0/com.ss.android.lark/shared_prefs/qa.xml) -join "`n"
    if([regex]::Matches($history,'qa_alpha').Count -le $beforeAlpha -or [regex]::Matches($history,'qa_beta').Count -le $beforeBeta) { throw 'Two simultaneous task entry links did not both reach the receiver.' }
    $focused=(Adb shell dumpsys activity activities) -join "`n"
    if($focused -notmatch 'topResumedActivity=.*cn.fangq.qa.feishu.TargetActivity') { throw 'Target did not become foreground.' }
    Write-Output 'PASS: simultaneous custom tasks independently opened alpha and beta entry links from background.'
} finally {
    if($prepared) {
        $restore=(Adb shell am instrument -w -e scenario restore cn.fangq.daka.test/cn.fangq.daka.SettingsInstrumentation) -join "`n"
        if($restore -notmatch 'PASS restored') { throw $restore }
        Write-Output 'Original tasks and reminder preferences restored.'
    }
}


