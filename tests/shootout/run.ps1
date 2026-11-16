$ErrorActionPreference='Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to Java 25' }
Push-Location (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
try {
 New-Item -ItemType Directory -Force build/shootout-audit/classes | Out-Null
 @'
gradle.projectsEvaluated {
 rootProject.tasks.register('writeShootoutClasspath') {
  doLast { rootProject.file('build/shootout-audit/classpath.txt').text = rootProject.sourceSets.client.runtimeClasspath.asPath }
 }
}
'@ | Set-Content -Encoding ASCII build/shootout-audit/classpath.init.gradle
 & ./gradlew.bat -I build/shootout-audit/classpath.init.gradle writeShootoutClasspath --offline --quiet
 if ($LASTEXITCODE -ne 0) { throw 'Classpath resolution failed' }
 $shootoutClasspath=(Get-Content -Raw build/shootout-audit/classpath.txt).Trim()
 & "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -cp $shootoutClasspath -d build/shootout-audit/classes tests/shootout/ShootoutRegression.java tests/shootout/ShotConfirmationRegression.java tests/shootout/AutoStartRegression.java tests/shootout/ViewDeviationRegression.java tests/shootout/AimCorrectionRegression.java tests/shootout/InterceptESPRegression.java tests/shootout/ArrowConfirmationToggleRegression.java tests/shootout/ShootoutControlsRegression.java tests/shootout/ManualShotRegression.java tests/shootout/RttRegression.java tests/shootout/TargetRotationRegression.java tests/shootout/TanhAimRegression.java tests/shootout/ManualRestrictionRegression.java tests/shootout/ShootoutSettingsLayoutRegression.java tests/shootout/BabyStallRegression.java tests/shootout/AimRateRegression.java tests/shootout/RelockRegression.java tests/shootout/DeveloperLogsRegression.java tests/shootout/AimShotDiagnosticsRegression.java
 if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/shootout-audit/classes;"+$shootoutClasspath) ShootoutRegression
 if ($LASTEXITCODE -ne 0) { throw 'Shootout regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/shootout-audit/classes;"+$shootoutClasspath) ShotConfirmationRegression
 if ($LASTEXITCODE -ne 0) { throw 'Shot confirmation regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/shootout-audit/classes;"+$shootoutClasspath) AutoStartRegression
 if ($LASTEXITCODE -ne 0) { throw 'Auto start regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/shootout-audit/classes;"+$shootoutClasspath) ViewDeviationRegression
 if ($LASTEXITCODE -ne 0) { throw 'View deviation regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/shootout-audit/classes;"+$shootoutClasspath) AimCorrectionRegression
 if ($LASTEXITCODE -ne 0) { throw 'Aim correction regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/shootout-audit/classes;"+$shootoutClasspath) InterceptESPRegression
 if ($LASTEXITCODE -ne 0) { throw 'Intercept ESP regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/shootout-audit/classes;"+$shootoutClasspath) ArrowConfirmationToggleRegression
 if ($LASTEXITCODE -ne 0) { throw 'Confirmation toggle regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/shootout-audit/classes;"+$shootoutClasspath) ShootoutControlsRegression
 if ($LASTEXITCODE -ne 0) { throw 'Shootout controls regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/shootout-audit/classes;"+$shootoutClasspath) ManualShotRegression
 if ($LASTEXITCODE -ne 0) { throw 'Manual shot regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/shootout-audit/classes;"+$shootoutClasspath) RttRegression
 if ($LASTEXITCODE -ne 0) { throw 'RTT regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/shootout-audit/classes;"+$shootoutClasspath) TargetRotationRegression
 if ($LASTEXITCODE -ne 0) { throw 'Target rotation regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/shootout-audit/classes;"+$shootoutClasspath) TanhAimRegression
 if ($LASTEXITCODE -ne 0) { throw 'Tanh curve regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/shootout-audit/classes;"+$shootoutClasspath) ManualRestrictionRegression
 if ($LASTEXITCODE -ne 0) { throw 'Manual restriction regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/shootout-audit/classes;"+$shootoutClasspath) ShootoutSettingsLayoutRegression
 if ($LASTEXITCODE -ne 0) { throw 'Shootout settings layout regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/shootout-audit/classes;"+$shootoutClasspath) BabyStallRegression
 if ($LASTEXITCODE -ne 0) { throw 'Baby stall and empty selection regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/shootout-audit/classes;"+$shootoutClasspath) AimRateRegression
 if ($LASTEXITCODE -ne 0) { throw 'Aim update rate regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/shootout-audit/classes;"+$shootoutClasspath) RelockRegression
 if ($LASTEXITCODE -ne 0) { throw 'Target relock round regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/shootout-audit/classes;"+$shootoutClasspath) DeveloperLogsRegression
 if ($LASTEXITCODE -ne 0) { throw 'Developer logs/defaults/occlusion regression failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/shootout-audit/classes;"+$shootoutClasspath) AimShotDiagnosticsRegression
 if ($LASTEXITCODE -ne 0) { throw 'Aim/shot diagnostics regression failed' }
 New-Item -ItemType Directory -Force build/shootout-audit/input | Out-Null
 & "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -cp $shootoutClasspath -d build/shootout-audit/input tests/shootout/stubs/net/minecraft/client/Minecraft.java src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutMouseInput.java src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/view/NativeViewInput.java tests/shootout/ShootoutInputRegression.java
 if ($LASTEXITCODE -ne 0) { throw 'Input test compilation failed' }
 & "$env:JAVA_HOME/bin/java.exe" -cp ("build/shootout-audit/input;"+$shootoutClasspath) ShootoutInputRegression
 if ($LASTEXITCODE -ne 0) { throw 'Input regression failed' }
 & python tests/shootout/WiringAudit.py
 if ($LASTEXITCODE -ne 0) { throw 'Shootout static integration audit failed' }
} finally { Pop-Location }
