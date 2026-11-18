$ErrorActionPreference='Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to Java 25' }
Push-Location (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
try {
 New-Item -ItemType Directory -Force build/hotkey-audit/classes | Out-Null
 Set-Content -Encoding ASCII build/hotkey-audit/classpath.init.gradle "gradle.projectsEvaluated { rootProject.tasks.register('writeHotkeyClasspath') { doLast { rootProject.file('build/hotkey-audit/classpath.txt').text = rootProject.sourceSets.client.runtimeClasspath.asPath } } }"
 & ./gradlew.bat -I build/hotkey-audit/classpath.init.gradle writeHotkeyClasspath --offline --quiet
 if ($LASTEXITCODE -ne 0) { throw 'Classpath resolution failed' }
 $cp=(Get-Content -Raw build/hotkey-audit/classpath.txt).Trim()
 & "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -cp $cp -d build/hotkey-audit/classes tests/hotkeys/HotkeySwitchRegression.java
 if ($LASTEXITCODE -ne 0) { throw 'Hotkey test compilation failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/hotkey-audit/classes;"+$cp) HotkeySwitchRegression
 if ($LASTEXITCODE -ne 0) { throw 'Hotkey test failed' }
} finally { Pop-Location }
