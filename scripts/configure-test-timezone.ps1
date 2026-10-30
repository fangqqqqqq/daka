param(
    [string]$SdkPath = 'D:/Android_SDK',
    [string]$Serial = 'emulator-5580',
    [string]$TimeZone = 'Asia/Shanghai'
)
$ErrorActionPreference = 'Stop'
$taskAdb = Join-Path $SdkPath 'platform-tools/adb.exe'
if (!(Test-Path -LiteralPath $taskAdb)) { throw 'Android adb.exe is missing.' }
$taskReady = $false
for ($taskAttempt = 0; $taskAttempt -lt 90; $taskAttempt++) {
    # Windows PowerShell 5 treats expected adb offline messages as errors.
    $ErrorActionPreference = 'Continue'
    $taskBoot = & $taskAdb -s $Serial shell getprop sys.boot_completed 2>$null
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -eq 0 -and "$taskBoot".Trim() -eq '1') { $taskReady = $true; break }
    Start-Sleep -Seconds 2
}
if (!$taskReady) { throw 'Test emulator did not finish booting within 3 minutes.' }
$taskAvdName = & $taskAdb -s $Serial shell getprop ro.boot.qemu.avd_name
if ("$taskAvdName".Trim() -ne 'Daka_Test_API35') { throw 'Timezone configuration is limited to the Daka_Test_API35 emulator.' }
& $taskAdb -s $Serial shell cmd alarm set-timezone $TimeZone
if ($LASTEXITCODE -ne 0) { throw 'Could not set the test emulator timezone.' }
$taskActualZone = & $taskAdb -s $Serial shell getprop persist.sys.timezone
if ("$taskActualZone".Trim() -ne $TimeZone) { throw 'Timezone verification failed.' }
Write-Output "Test emulator timezone: $TimeZone"
& $taskAdb -s $Serial shell date
