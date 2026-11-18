param([switch]$HubOnly)
$ErrorActionPreference='Stop'
Push-Location (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
try {
 New-Item -ItemType Directory -Force build/path-audit | Out-Null
 Set-Content -Encoding ASCII build/path-audit/classpath.init.gradle "gradle.projectsEvaluated { rootProject.tasks.register('writePathClasspath') { doLast { rootProject.file('build/path-audit/classpath.txt').text = rootProject.sourceSets.client.runtimeClasspath.asPath } } }"
 & ./gradlew.bat -I build/path-audit/classpath.init.gradle writePathClasspath --offline --quiet
 if($LASTEXITCODE -ne 0){throw 'Path classpath resolution failed'}
 $cp=(Get-Content build/path-audit/classpath.txt -Raw).Trim()
 New-Item -ItemType Directory -Force build/path-audit/classes | Out-Null
 & "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -cp $cp -d build/path-audit/classes tests/path-finding/CorridorRegression.java tests/path-finding/PathRegression.java tests/path-finding/SearchAuditRegression.java tests/path-finding/MapScenarios.java tests/path-finding/PathStartupRegression.java tests/path-finding/TimedCorridorRegression.java
 if($LASTEXITCODE -ne 0){throw 'Path test compilation failed'}
 & "$env:JAVA_HOME/bin/java.exe" -Xmx2G -cp ("build/path-audit/classes;"+$cp) PathRegression
 if($LASTEXITCODE -ne 0){throw 'Path regression failed'}
 & "$env:JAVA_HOME/bin/java.exe" -Xmx2G -cp ("build/path-audit/classes;"+$cp) SearchAuditRegression
 if($LASTEXITCODE -ne 0){throw 'Search audit regression failed'}
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/path-audit/classes;"+$cp) CorridorRegression
 if($LASTEXITCODE -ne 0){throw 'Corridor regression failed'}
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/path-audit/classes;"+$cp) PathStartupRegression
 if($LASTEXITCODE -ne 0){throw 'Path startup regression failed'}
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/path-audit/classes;"+$cp) TimedCorridorRegression
 if($LASTEXITCODE -ne 0){throw "Timed corridor regression failed"}
 & "$env:JAVA_HOME/bin/java.exe" -Xmx2G -cp ("build/path-audit/classes;"+$cp) MapScenarios $(if($HubOnly){'--hub-only'})
 if($LASTEXITCODE -ne 0){throw 'Map scenarios failed'}
} finally {Pop-Location}
