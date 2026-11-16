param([Parameter(Mandatory=$true)][string]$LitematicaJar,[Parameter(Mandatory=$true)][string]$MalilibJar)
$ErrorActionPreference='Stop'
Push-Location (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
try {
 $cp=(Get-Content -Raw build/mapping-audit/classpath.txt).Trim()+';'+(Resolve-Path $LitematicaJar).Path+';'+(Resolve-Path $MalilibJar).Path
 & "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -cp $cp -d build/mapping-audit/classes tests/mapping/LitematicaCompatibility.java
 if($LASTEXITCODE -ne 0){throw 'Oracle compilation failed'}
 & "$env:JAVA_HOME/bin/java.exe" -cp ('build/mapping-audit/classes;'+$cp) LitematicaCompatibility
 if($LASTEXITCODE -ne 0){throw 'Litematica external compatibility failed'}
} finally {Pop-Location}

exit 0
