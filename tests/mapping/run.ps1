$ErrorActionPreference='Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to Java 25' }
Push-Location (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
try {
 New-Item -ItemType Directory -Force build/mapping-audit/classes | Out-Null
 Set-Content -Encoding ASCII build/mapping-audit/classpath.init.gradle "gradle.projectsEvaluated { rootProject.tasks.register('writeMappingClasspath') { doLast { rootProject.file('build/mapping-audit/classpath.txt').text = rootProject.sourceSets.client.runtimeClasspath.asPath } } }"
 & ./gradlew.bat -I build/mapping-audit/classpath.init.gradle writeMappingClasspath --offline --quiet
 if ($LASTEXITCODE -ne 0) { throw 'Classpath resolution failed' }
 $cp=(Get-Content -Raw build/mapping-audit/classpath.txt).Trim()
 & "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -cp $cp -d build/mapping-audit/classes tests/mapping/MappingRegression.java tests/mapping/ReplayRegression.java tests/mapping/RecorderHudIntegration.java
 if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/mapping-audit/classes;"+$cp) MappingRegression
 if ($LASTEXITCODE -ne 0) { throw 'Mapping regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/mapping-audit/classes;"+$cp) ReplayRegression
 if ($LASTEXITCODE -ne 0) { throw 'Replay regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/mapping-audit/classes;"+$cp) RecorderHudIntegration
 if ($LASTEXITCODE -ne 0) { throw 'Recorder HUD integration failed' }
 python tests/mapping/check_wiring.py
 if ($LASTEXITCODE -ne 0) { throw 'Mapping wiring audit failed' }
} finally { Pop-Location }
