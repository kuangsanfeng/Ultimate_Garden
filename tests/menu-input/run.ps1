$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to Java 25.' }
Push-Location (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
try {
    New-Item -ItemType Directory -Force build/menu-input-audit/classes | Out-Null
    $stubs = @(Get-ChildItem tests/menu-input/stubs -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
    & (Join-Path $env:JAVA_HOME 'bin/javac.exe') -encoding UTF-8 -d build/menu-input-audit/classes @stubs src/client/java/com/kuangsanfeng/ultimategarden/client/input/VirtualInput.java src/client/java/com/kuangsanfeng/ultimategarden/client/input/VirtualMouse.java src/client/java/com/kuangsanfeng/ultimategarden/client/scheduler/ActionScheduler.java tests/menu-input/MenuInputRegression.java
    if ($LASTEXITCODE -ne 0) { throw 'Menu input compilation failed.' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp build/menu-input-audit/classes MenuInputRegression
    if ($LASTEXITCODE -ne 0) { throw 'Menu input regression failed.' }
} finally { Pop-Location }
