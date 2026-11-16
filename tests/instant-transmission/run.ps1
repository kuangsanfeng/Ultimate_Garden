$ErrorActionPreference='Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to Java 25' }
Push-Location (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
try {
 New-Item -ItemType Directory -Force build/transmission-audit/classes | Out-Null
 Set-Content -Encoding ASCII build/transmission-audit/classpath.init.gradle "gradle.projectsEvaluated { rootProject.tasks.register('writeTransmissionClasspath') { doLast { rootProject.file('build/transmission-audit/classpath.txt').text = rootProject.sourceSets.client.runtimeClasspath.asPath } } }"
 & ./gradlew.bat -I build/transmission-audit/classpath.init.gradle writeTransmissionClasspath --offline --quiet
 if ($LASTEXITCODE -ne 0) { throw 'Classpath resolution failed' }
 $cp=(Get-Content -Raw build/transmission-audit/classpath.txt).Trim()
 & "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -cp $cp -d build/transmission-audit/classes tests/instant-transmission/TransmissionRegression.java tests/instant-transmission/ResetDiagnosticsRegression.java tests/instant-transmission/FlightRoutingRegression.java
 if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/transmission-audit/classes;"+$cp) TransmissionRegression
 if ($LASTEXITCODE -ne 0) { throw 'Transmission regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/transmission-audit/classes;"+$cp) ResetDiagnosticsRegression
 if ($LASTEXITCODE -ne 0) { throw 'Reset diagnostics regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/transmission-audit/classes;"+$cp) FlightRoutingRegression
 if ($LASTEXITCODE -ne 0) { throw 'Flight routing regression failed' }
} finally { Pop-Location }
