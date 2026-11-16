$ErrorActionPreference='Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to Java 25' }
Push-Location (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
try {
 New-Item -ItemType Directory -Force build/context-audit/classes | Out-Null
 $init = 'gradle.projectsEvaluated { rootProject.tasks.register("contextClasspath") { doLast { rootProject.file("build/context-audit/classpath.txt").text = rootProject.sourceSets.client.runtimeClasspath.asPath } } }'
 Set-Content -Encoding ASCII build/context-audit/classpath.init.gradle $init
 & ./gradlew.bat -I build/context-audit/classpath.init.gradle contextClasspath --offline --quiet
 if ($LASTEXITCODE -ne 0) { throw 'Classpath resolution failed' }
 $cp=(Get-Content -Raw build/context-audit/classpath.txt).Trim()
 $files=@(Get-ChildItem tests/context -Filter '*Regression.java')
 & "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -cp $cp -d build/context-audit/classes @($files.FullName)
 if ($LASTEXITCODE -ne 0) { throw 'Common regression compilation failed' }
 foreach ($file in $files) {
  & "$env:JAVA_HOME/bin/java.exe" -cp ("build/context-audit/classes;"+$cp) $file.BaseName
  if ($LASTEXITCODE -ne 0) { throw ("Common regression failed: "+$file.BaseName) }
 }
} finally { Pop-Location }
