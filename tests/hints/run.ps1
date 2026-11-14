$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to Java 25.' }
Push-Location (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
try {
    $cacheRoot = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
    $dependencies = @()
    foreach ($pattern in @('gson-*.jar','slf4j-api-*.jar','fabric-loader-*.jar')) {
        $dependencies += Get-ChildItem (Join-Path $cacheRoot 'caches/modules-2/files-2.1') -Recurse -File -Filter $pattern | Where-Object { $_.Name -notmatch '(sources|javadoc)' } | Sort-Object FullName | Select-Object -Last 1 -ExpandProperty FullName
    }
    $classpath = 'build/classes/java/client;build/classes/java/main;build/resources/client;build/resources/main;' + ($dependencies -join ';')
    New-Item -ItemType Directory -Force build/hint-audit/classes | Out-Null
    $stubs = @(Get-ChildItem tests/hints/stubs -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
    & (Join-Path $env:JAVA_HOME 'bin/javac.exe') -encoding UTF-8 -cp $classpath -d build/hint-audit/classes @stubs src/client/java/com/kuangsanfeng/ultimategarden/client/hint/HintManager.java src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/hint/FarmHints.java tests/hints/HintRegression.java
    if ($LASTEXITCODE -ne 0) { throw 'Hint test compilation failed' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp ('build/hint-audit/classes;' + $classpath) HintRegression
    if ($LASTEXITCODE -ne 0) { throw 'Hint regression failed' }
} finally { Pop-Location }
