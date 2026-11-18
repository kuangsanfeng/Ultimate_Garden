$ErrorActionPreference='Stop'
Push-Location (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
try {
 New-Item -ItemType Directory -Force build/path-audit/classes | Out-Null
 $cp=(Get-Content build/path-audit/classpath.txt -Raw).Trim()
 & "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -cp $cp -d build/path-audit/classes tests/path-finding/HubSearchAudit.java
 if($LASTEXITCODE -ne 0){throw 'Hub audit compilation failed'}
 [IO.File]::WriteAllText((Join-Path (Get-Location) 'build/path-audit/hub-pathfinding.jsonl'),'',(New-Object System.Text.UTF8Encoding($false)))
 foreach($scenario in @('H1','H2')) {foreach($mode in @('NORMAL','WEIGHTED')) {
  & "$env:JAVA_HOME/bin/java.exe" -Xmx3G -cp ("build/path-audit/classes;"+$cp) HubSearchAudit $scenario $mode
  if($LASTEXITCODE -ne 0){
   $exitCode=$LASTEXITCODE
   $tail=Get-Content build/path-audit/hub-pathfinding.jsonl -Tail 1 | ConvertFrom-Json
   if($tail.event -ne 'RUN_END' -or $tail.runId -ne "$scenario-$mode-200k"){
    $row=[ordered]@{event='RUN_END';runId="$scenario-$mode-200k";scenario=$scenario;mode=$mode;data=@{status='PROCESS_FAILURE';exitCode=$exitCode;reason='JVM exited without terminal record';clientExecuted=$false;latestProgress=$tail}}
    [IO.File]::AppendAllText((Join-Path (Get-Location) 'build/path-audit/hub-pathfinding.jsonl'),($row | ConvertTo-Json -Depth 30 -Compress)+[Environment]::NewLine,(New-Object System.Text.UTF8Encoding($false)))
   }
   Write-Warning "$scenario $mode process exited $exitCode; continuing remaining searches"
  }
 }}
} finally {Pop-Location}
