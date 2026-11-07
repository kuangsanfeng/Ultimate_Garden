$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to Java 25.' }
Push-Location (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
try {
    $outputPath = Join-Path (Get-Location).Path 'build/route-audit/classes-v2'
    New-Item -ItemType Directory -Force $outputPath | Out-Null
    $cacheRoot = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
    $dependencies = @()
    foreach ($pattern in @('gson-*.jar', 'slf4j-api-*.jar', 'fabric-loader-*.jar')) {
        $dependency = Get-ChildItem (Join-Path $cacheRoot 'caches/modules-2/files-2.1') -Recurse -File -Filter $pattern | Where-Object { $_.Name -notmatch '(sources|javadoc)' } | Sort-Object FullName | Select-Object -Last 1
        if (-not $dependency) { throw "Missing $pattern" }
        $dependencies += $dependency.FullName
    }
    $classpath = 'build/classes/java/client;build/classes/java/main;build/resources/client;build/resources/main;' + ($dependencies -join ';')
    $stubs = @(Get-ChildItem tests/route/stubs -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
    & (Join-Path $env:JAVA_HOME 'bin/javac.exe') -encoding UTF-8 -cp $classpath -d $outputPath @stubs tests/gui/stubs/net/minecraft/client/Minecraft.java tests/route/RowTimingRegression.java tests/route/RandomDelayRuntimeRegression.java
    if ($LASTEXITCODE -ne 0) { throw 'Route test compilation failed.' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp ('build/route-audit/classes-v2;' + $classpath) RowTimingRegression
    if ($LASTEXITCODE -ne 0) { throw 'Route test failed.' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp ('build/route-audit/classes-v2;' + $classpath) RandomDelayRuntimeRegression
    if ($LASTEXITCODE -ne 0) { throw 'Random delay call-site test failed.' }
} finally { Pop-Location }
