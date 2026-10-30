param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$SdkPath = $env:ANDROID_HOME,
    [string]$GradleCache = $env:GRADLE_USER_HOME
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrEmpty($JavaHome) -or !(Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) {
    throw 'Provide -JavaHome pointing to JDK 17.'
}
if ([string]::IsNullOrEmpty($SdkPath) -or !(Test-Path -LiteralPath (Join-Path $SdkPath 'platforms\android-35\android.jar'))) {
    throw 'Provide -SdkPath pointing to an Android SDK with API 35.'
}
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $SdkPath
$env:JAVA_OPTS = '-Xmx64m'
$taskArgs = @('assembleDebug','testDebugUnitTest','lintDebug','--no-daemon')
if (![string]::IsNullOrEmpty($GradleCache)) { $taskArgs += @('-g',$GradleCache) }
Push-Location $taskRoot
try {
    & .\gradlew.bat @taskArgs
    if ($LASTEXITCODE -ne 0) { throw 'Android build or verification failed.' }
    $taskDist = Join-Path $taskRoot 'dist'
    New-Item -ItemType Directory -Path $taskDist -Force | Out-Null
    $taskGradle = Get-Content -LiteralPath (Join-Path $taskRoot 'app/build.gradle') -Raw
    $taskVersion = [regex]::Match($taskGradle, "versionName\s+'([^']+)'").Groups[1].Value
    if (!$taskVersion) { throw 'Cannot read app versionName.' }
    $taskApkName = "feishu-daka-$taskVersion-debug.apk"
    $taskApk = Join-Path $taskDist $taskApkName
    Copy-Item -LiteralPath (Join-Path $taskRoot 'app\build\outputs\apk\debug\app-debug.apk') -Destination $taskApk -Force
    $taskHash = Get-FileHash -LiteralPath $taskApk -Algorithm SHA256
    Set-Content -LiteralPath (Join-Path $taskDist 'SHA256SUMS.txt') -Value ($taskHash.Hash.ToLowerInvariant() + '  ' + $taskApkName) -Encoding ascii
    Write-Output "APK: $taskApk"
} finally { Pop-Location }

