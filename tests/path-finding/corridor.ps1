param([switch]$Segmented,[switch]$Timed)
$ErrorActionPreference='Stop'
Push-Location (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
try {
 New-Item -ItemType Directory -Force build/path-audit/classes | Out-Null
 $cp=(Get-Content build/path-audit/classpath.txt -Raw).Trim()
 & "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -cp $cp -d build/path-audit/classes tests/path-finding/CorridorSearchAudit.java
 if($LASTEXITCODE -ne 0){throw 'Hub audit compilation failed'}
 if($Timed){$Segmented=$true}
 $log=if($Timed){'build/path-audit/timed-corridor-pathfinding.jsonl'}elseif($Segmented){'build/path-audit/segmented-pathfinding.jsonl'}else{'build/path-audit/corridor-pathfinding.jsonl'}
 $tag=if($Timed){'timed'}elseif($Segmented){'segmented'}else{'200k'}
 $auditMode = "-Dug.pathAudit.segmented="+([bool]$Segmented).ToString().ToLowerInvariant()
 $timedMode = "-Dug.pathAudit.timed="+([bool]$Timed).ToString().ToLowerInvariant()
 [IO.File]::WriteAllText((Join-Path (Get-Location) $log),'',(New-Object System.Text.UTF8Encoding($false)))
 $scenarios=if($Segmented){@('H1','H2','D1','DESCENT')}else{@('H1','H2','D1')}
 foreach($scenario in $scenarios) {foreach($mode in $(if($scenario -eq 'DESCENT'){'NORMAL'}else{'NORMAL','WEIGHTED'})) {
  & "$env:JAVA_HOME/bin/java.exe" -Xmx3G $auditMode $timedMode -cp ("build/path-audit/classes;"+$cp) CorridorSearchAudit $scenario $mode
  if($LASTEXITCODE -ne 0){
   $exitCode=$LASTEXITCODE
   $tail=Get-Content $log -Tail 1 | ConvertFrom-Json
   if($tail.event -ne 'RUN_END' -or $tail.runId -ne "$scenario-$mode-$tag"){
    $row=[ordered]@{event='RUN_END';runId="$scenario-$mode-$tag";scenario=$scenario;mode=$mode;data=@{status='PROCESS_FAILURE';exitCode=$exitCode;reason='JVM exited without terminal record';clientExecuted=$false;latestProgress=$tail}}
    [IO.File]::AppendAllText((Join-Path (Get-Location) $log),($row | ConvertTo-Json -Depth 30 -Compress)+[Environment]::NewLine,(New-Object System.Text.UTF8Encoding($false)))
   }
   Write-Warning "$scenario $mode process exited $exitCode; continuing remaining searches"
  }
 }}
} finally {Pop-Location}
