$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to the Java 25 JDK.' }
Push-Location (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
try {
    New-Item -ItemType Directory -Force build/gui-audit/test-classes | Out-Null
    New-Item -ItemType Directory -Force build/gui-audit/render-classes | Out-Null
    & (Join-Path $env:JAVA_HOME 'bin/javac.exe') -encoding UTF-8 -d build/gui-audit/render-classes tests/gui/render-stubs/net/minecraft/client/gui/GuiGraphicsExtractor.java src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/render/RoundedRect.java tests/gui/RoundedGeometryRegression.java
    if ($LASTEXITCODE -ne 0) { throw 'Rounded geometry compilation failed.' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp build/gui-audit/render-classes RoundedGeometryRegression
    if ($LASTEXITCODE -ne 0) { throw 'Rounded geometry regression failed.' }

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
    $cacheRoot = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
    $gson = Get-ChildItem (Join-Path $cacheRoot 'caches/modules-2/files-2.1') -Recurse -File -Filter 'gson-*.jar' |
        Where-Object { $_.Name -notmatch '(sources|javadoc)' } | Sort-Object FullName | Select-Object -Last 1
    if (-not $gson) { throw 'Build the project first; Gson was not found in the dependency cache.' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp ('build/gui-audit/test-classes;build/classes/java/client;' + $gson.FullName) PestMenuRegression
    if ($LASTEXITCODE -ne 0) { throw 'Pest/menu regression failed.' }
} finally { Pop-Location }
