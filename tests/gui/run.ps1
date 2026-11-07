$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to the Java 25 JDK.' }
Push-Location (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
try {
    New-Item -ItemType Directory -Force build/gui-audit/test-classes | Out-Null
    & (Join-Path $env:JAVA_HOME 'bin/javac.exe') -encoding UTF-8 -cp build/classes/java/client -d build/gui-audit/test-classes tests/gui/GuiLifecycleRegression.java tests/gui/CollapsibleSectionRegression.java tests/gui/HelpTooltipRegression.java
    if ($LASTEXITCODE -ne 0) { throw 'GUI regression compilation failed.' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp 'build/gui-audit/test-classes;build/classes/java/client' GuiLifecycleRegression
    if ($LASTEXITCODE -ne 0) { throw 'GUI regression failed.' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp 'build/gui-audit/test-classes;build/classes/java/client' CollapsibleSectionRegression
    if ($LASTEXITCODE -ne 0) { throw 'Section layout regression failed.' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp 'build/gui-audit/test-classes;build/classes/java/client' HelpTooltipRegression
    if ($LASTEXITCODE -ne 0) { throw 'Tooltip timing regression failed.' }
    $stubs = @(Get-ChildItem tests/gui/stubs -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
    & (Join-Path $env:JAVA_HOME 'bin/javac.exe') -encoding UTF-8 -cp build/classes/java/client -d build/gui-audit/test-classes @stubs tests/gui/PestMenuRegression.java
    if ($LASTEXITCODE -ne 0) { throw 'Pest/menu regression compilation failed.' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp 'build/gui-audit/test-classes;build/classes/java/client' PestMenuRegression
    if ($LASTEXITCODE -ne 0) { throw 'Pest/menu regression failed.' }
} finally { Pop-Location }
