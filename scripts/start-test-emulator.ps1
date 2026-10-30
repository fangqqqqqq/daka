param(
    [string]$SdkPath = 'D:/Android_SDK',
    [string]$TimeZone = 'Asia/Shanghai',
    [switch]$Headless
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskAvdHome = Join-Path $taskRoot '.tools/avd'
$taskEmulator = Join-Path $SdkPath 'emulator/emulator.exe'
if (!(Test-Path -LiteralPath $taskEmulator) -or !(Test-Path -LiteralPath (Join-Path $taskAvdHome 'Daka_Test_API35.ini'))) {
    throw 'The local test AVD or Android Emulator is missing. Create an Android 15 AVD in Android Studio first; see README.md.'
}
$env:ANDROID_AVD_HOME = $taskAvdHome
$env:ANDROID_HOME = $SdkPath
$taskAdb = Join-Path $SdkPath 'platform-tools/adb.exe'
$ErrorActionPreference = 'Continue'
$taskExistingName = & $taskAdb -s emulator-5580 shell getprop ro.boot.qemu.avd_name 2>$null
$ErrorActionPreference = 'Stop'
if ("$taskExistingName".Trim() -eq 'Daka_Test_API35') {
    Write-Output 'Using the running project test emulator.'
    & (Join-Path $PSScriptRoot 'configure-test-timezone.ps1') -SdkPath $SdkPath -TimeZone $TimeZone
    return
}
$taskArgs = @('-avd','Daka_Test_API35','-no-snapshot','-port','5580','-feature','-Vulkan','-gpu','swiftshader_indirect')
if ($Headless) {
    $taskArgs += @('-no-window','-no-audio')
    $taskProcess = Start-Process -FilePath $taskEmulator -ArgumentList $taskArgs -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $taskRoot '.tools/emulator.stdout.log') `
        -RedirectStandardError (Join-Path $taskRoot '.tools/emulator.stderr.log')
    Write-Output "Headless test emulator PID: $($taskProcess.Id)"
} else {
    # Run this branch yourself to open the interactive Android window.
    $taskProcess = Start-Process -FilePath $taskEmulator -ArgumentList $taskArgs -PassThru
}
& (Join-Path $PSScriptRoot 'configure-test-timezone.ps1') -SdkPath $SdkPath -TimeZone $TimeZone
