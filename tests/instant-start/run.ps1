param([switch]$ReturnOnly)
$ErrorActionPreference='Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to Java 25' }
Push-Location (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
try {
 New-Item -ItemType Directory -Force build/instant-start-audit/classes | Out-Null
 Set-Content -Encoding ASCII build/instant-start-audit/classpath.init.gradle "gradle.projectsEvaluated { rootProject.tasks.register('writeStartClasspath') { doLast { rootProject.file('build/instant-start-audit/classpath.txt').text = rootProject.sourceSets.client.runtimeClasspath.asPath } } }"
 & ./gradlew.bat -I build/instant-start-audit/classpath.init.gradle writeStartClasspath --offline --quiet
 if ($LASTEXITCODE -ne 0) { throw 'Classpath failed' }
 $cp=(Get-Content -Raw build/instant-start-audit/classpath.txt).Trim()
 $stubs=@(Get-ChildItem tests/instant-start/stubs -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
 & "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -cp $cp -d build/instant-start-audit/classes @stubs src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/InstantTransmissionController.java tests/instant-start/StartRegression.java tests/instant-start/ReturnRegression.java tests/instant-start/YControllerRegression.java
 if ($LASTEXITCODE -ne 0) { throw 'Startup compilation failed' }
 if (-not $ReturnOnly) {
  & "$env:JAVA_HOME/bin/java.exe" -cp ("build/instant-start-audit/classes;"+$cp) StartRegression
  if ($LASTEXITCODE -ne 0) { throw 'Startup regression failed' }
 }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/instant-start-audit/classes;"+$cp) ReturnRegression
 if ($LASTEXITCODE -ne 0) { throw 'Return regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/instant-start-audit/classes;"+$cp) YControllerRegression
 if ($LASTEXITCODE -ne 0) { throw "Y controller regression failed" }
} finally { Pop-Location }
