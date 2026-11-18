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
    New-Item -ItemType Directory -Force build/hud-adjust-audit/classes | Out-Null
    $stubs = @(Get-ChildItem tests/hud-adjust/stubs -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
    & (Join-Path $env:JAVA_HOME 'bin/javac.exe') -encoding UTF-8 -cp $classpath -d build/hud-adjust-audit/classes @stubs src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/HudAdjustmentController.java src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/instant_transmission/InstantTransmissionHud.java tests/hud-adjust/HudAdjustmentRegression.java tests/hud-adjust/TransmissionHudRenderRegression.java tests/hud-adjust/HudPanelsRegression.java tests/hud-adjust/MappingLocationRegression.java tests/hud-adjust/WorldFilterRegression.java src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/mapping/MappingHud.java src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/zombie_shoot/ZombieShootoutHud.java
    if ($LASTEXITCODE -ne 0) { throw 'HUD adjustment test compilation failed' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp ('build/hud-adjust-audit/classes;' + $classpath) HudAdjustmentRegression
    if ($LASTEXITCODE -ne 0) { throw 'HUD adjustment test failed' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp ('build/hud-adjust-audit/classes;' + $classpath) TransmissionHudRenderRegression
    if ($LASTEXITCODE -ne 0) { throw 'Transmission HUD visibility test failed' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp ('build/hud-adjust-audit/classes;' + $classpath) HudPanelsRegression
    if ($LASTEXITCODE -ne 0) { throw 'HUD panels regression failed' }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp ('build/hud-adjust-audit/classes;' + $classpath) MappingLocationRegression
    if ($LASTEXITCODE -ne 0) { throw 'Mapping location regression failed' }
 & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp ('build/hud-adjust-audit/classes;' + $classpath) WorldFilterRegression
 if ($LASTEXITCODE -ne 0) { throw "World filter regression failed" }
} finally { Pop-Location }
