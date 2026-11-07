$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to the Java 25 JDK.' }
Push-Location (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
try {
    New-Item -ItemType Directory -Force build/focus-audit/test-classes | Out-Null
    $stubs = @(Get-ChildItem tests/focus/stubs -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
    & (Join-Path $env:JAVA_HOME 'bin/javac.exe') -encoding UTF-8 -d build/focus-audit/test-classes @stubs src/client/java/com/kuangsanfeng/ultimategarden/client/window/WindowsState.java src/client/java/com/kuangsanfeng/ultimategarden/client/window/WindowsInputInterceptor.java src/client/java/com/kuangsanfeng/ultimategarden/client/window/WindowsFocusController.java tests/focus/FocusRegression.java
    if ($LASTEXITCODE -ne 0) { throw 'Focus regression compilation failed.' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp build/focus-audit/test-classes FocusRegression
    if ($LASTEXITCODE -ne 0) { throw 'Focus regression failed.' }
} finally { Pop-Location }
