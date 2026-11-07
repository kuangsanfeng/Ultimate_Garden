$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to the Java 25 JDK.' }
Push-Location (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
try {
    New-Item -ItemType Directory -Force build/esp-audit/classes | Out-Null
    @'
gradle.projectsEvaluated {
    rootProject.tasks.register('writeESPTestClasspath') {
        doLast { rootProject.file('build/esp-audit/classpath.txt').text = rootProject.sourceSets.client.runtimeClasspath.asPath }
    }
}
'@ | Set-Content -Encoding ASCII build/esp-audit/classpath.init.gradle
    & .\gradlew.bat -I build/esp-audit/classpath.init.gradle writeESPTestClasspath --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve existing runtime classpath.' }
    $classpath = (Get-Content -Raw build/esp-audit/classpath.txt).Replace('\', '/')
    [IO.File]::WriteAllText((Join-Path $PWD 'build/esp-audit/javac.args'), "-encoding UTF-8 -cp `"$classpath`" -d build/esp-audit/classes tests/esp/ESPRegression.java tests/esp/TracerShaderRegression.java", [Text.UTF8Encoding]::new($false))
    & (Join-Path $env:JAVA_HOME 'bin/javac.exe') '@build/esp-audit/javac.args'
    if ($LASTEXITCODE -ne 0) { throw 'ESP regression compilation failed.' }
    [IO.File]::WriteAllText((Join-Path $PWD 'build/esp-audit/java.args'), "-cp `"build/esp-audit/classes;$classpath`" ESPRegression", [Text.UTF8Encoding]::new($false))
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') '@build/esp-audit/java.args'
    if ($LASTEXITCODE -ne 0) { throw 'ESP regression failed.' }
    [IO.File]::WriteAllText((Join-Path $PWD 'build/esp-audit/java.args'), "-cp `"build/esp-audit/classes;$classpath`" TracerShaderRegression", [Text.UTF8Encoding]::new($false))
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') '@build/esp-audit/java.args'
    if ($LASTEXITCODE -ne 0) { throw 'Tracer GPU regression failed.' }
} finally { Pop-Location }
