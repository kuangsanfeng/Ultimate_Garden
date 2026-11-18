$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to the Java 25 JDK before running this script.' }
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $projectRoot
try {
    $cacheRoot = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
    $dependencies = @()
    foreach ($pattern in @('gson-*.jar', 'slf4j-api-*.jar', 'fabric-loader-*.jar')) {
        $dependency = Get-ChildItem (Join-Path $cacheRoot 'caches/modules-2/files-2.1') -Recurse -File -Filter $pattern |
            Where-Object { $_.Name -notmatch '(sources|javadoc)' } | Sort-Object FullName | Select-Object -Last 1
        if (-not $dependency) { throw "Build the project first; missing dependency $pattern" }
        $dependencies += $dependency.FullName
    }
    $classpath = 'build/classes/java/client;build/classes/java/main;build/resources/client;build/resources/main;' + ($dependencies -join ';')
    New-Item -ItemType Directory -Force build/config-audit/classes | Out-Null
    & (Join-Path $env:JAVA_HOME 'bin/javac.exe') -encoding UTF-8 -cp $classpath -d build/config-audit/classes tests/config/StartupCorrectionRegression.java tests/config/HeaderToggleConfigRegression.java tests/config/ConfigRegression.java tests/config/AccountRegression.java tests/config/LineChangeConfigRegression.java tests/config/RandomDelayRegression.java tests/config/MenuPauseConfigRegression.java tests/config/HudConfigRegression.java tests/gui/LanguageRegression.java tests/gui/TransparencyRegression.java
    if ($LASTEXITCODE -ne 0) { throw 'Regression test compilation failed.' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp ('build/config-audit/classes;' + $classpath) StartupCorrectionRegression
    if ($LASTEXITCODE -ne 0) { throw 'Startup correction regression failed.' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp ('build/config-audit/classes;' + $classpath) HeaderToggleConfigRegression
    if ($LASTEXITCODE -ne 0) { throw 'Header switch configuration regression failed.' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp ('build/config-audit/classes;' + $classpath) ConfigRegression
    if ($LASTEXITCODE -ne 0) { throw 'Configuration regression checks failed.' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp ('build/config-audit/classes;' + $classpath) RandomDelayRegression
    if ($LASTEXITCODE -ne 0) { throw 'Random delay regression failed.' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp ('build/config-audit/classes;' + $classpath) MenuPauseConfigRegression
    if ($LASTEXITCODE -ne 0) { throw 'Menu auto-pause regression failed.' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp ('build/config-audit/classes;' + $classpath) HudConfigRegression
    if ($LASTEXITCODE -ne 0) { throw 'HUD regression failed.' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp ('build/config-audit/classes;' + $classpath) LineChangeConfigRegression
    if ($LASTEXITCODE -ne 0) { throw 'Line-change configuration checks failed.' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp ('build/config-audit/classes;' + $classpath) AccountRegression
    if ($LASTEXITCODE -ne 0) { throw 'Account regression checks failed.' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp ('build/config-audit/classes;' + $classpath) LanguageRegression
    if ($LASTEXITCODE -ne 0) { throw 'Language regression checks failed.' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp ('build/config-audit/classes;' + $classpath) TransparencyRegression
    if ($LASTEXITCODE -ne 0) { throw 'Transparency regression checks failed.' }
} finally { Pop-Location }
