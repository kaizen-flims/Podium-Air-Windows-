# Run in a Visual Studio x64 developer shell (GitHub Actions configures one).
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$output = Join-Path $root 'build/windows-helper'
New-Item -ItemType Directory -Force $output | Out-Null
& cl.exe /nologo /std:c++20 /EHsc /O2 /MT /DUNICODE /D_UNICODE "$PSScriptRoot/media-bridge.cpp" "/Fo$output/bridge.obj" "/Fe$output/PodiumMediaBridge.exe" /link runtimeobject.lib ole32.lib oleaut32.lib user32.lib shell32.lib
if ($LASTEXITCODE -ne 0) { throw 'Native Windows media helper compilation failed' }
