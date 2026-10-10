# Run in a Visual Studio x64 developer shell (GitHub Actions configures one).
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$output = Join-Path $root 'build/windows-helper'
New-Item -ItemType Directory -Force $output | Out-Null
Push-Location $root
try {
    & rc.exe /nologo "/fo$output/bridge.res" "$PSScriptRoot/media-bridge.rc"
    if ($LASTEXITCODE -ne 0) { throw 'Native Windows icon/version resource compilation failed' }
} finally { Pop-Location }
& cl.exe /nologo /std:c++20 /EHsc /O2 /MT /DUNICODE /D_UNICODE "$PSScriptRoot/media-bridge.cpp" "/Fo$output/bridge.obj" "/Fe$output/PodiumMediaBridge.exe" "$output/bridge.res" /link /MANIFEST:EMBED "/MANIFESTINPUT:$PSScriptRoot/media-bridge.manifest" runtimeobject.lib ole32.lib oleaut32.lib user32.lib shell32.lib
if ($LASTEXITCODE -ne 0) { throw 'Native Windows media helper compilation failed' }

$analysis = Join-Path $root 'build/windows-analysis'
New-Item -ItemType Directory -Force $analysis | Out-Null
$include = Join-Path $env:JAVA_HOME 'include'
Push-Location $analysis
try {
    & cl.exe /nologo /std:c++20 /EHsc /O2 /MT /LD "/I$include" "/I$include/win32" "/I$root/native" "$PSScriptRoot/analysis_jni.cpp" "$PSScriptRoot/mel_jni.cpp" "$PSScriptRoot/vocal_jni.cpp" "$root/native/analyzer/mel_spectrogram.cpp" "$root/native/analyzer/vocal_spectrogram.cpp" "$root/native/analyzer/audio_analysis.cpp" "$root/native/analyzer/tempo_analysis.cpp" "$root/native/analyzer/resampler.cpp" /FePodiumAnalysis.dll
    if ($LASTEXITCODE -ne 0) { throw 'Native Automix analysis library compilation failed' }
    & cl.exe /nologo /std:c++20 /EHsc /O2 /MT "/I$root/native" "$PSScriptRoot/analysis-self-test.cpp" "$root/native/analyzer/mel_spectrogram.cpp" "$root/native/analyzer/vocal_spectrogram.cpp" "$root/native/analyzer/audio_analysis.cpp" "$root/native/analyzer/tempo_analysis.cpp" "$root/native/analyzer/resampler.cpp" /FeAnalysisSelfTest.exe
    if ($LASTEXITCODE -ne 0) { throw 'Native Automix analysis test compilation failed' }
    & ./AnalysisSelfTest.exe
    if ($LASTEXITCODE -ne 0) { throw 'Native Automix analysis self-test failed' }
} finally { Pop-Location }
