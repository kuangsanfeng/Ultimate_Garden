import json,collections,pathlib
p=pathlib.Path('build/path-audit/hub-pathfinding.jsonl')
runs=collections.defaultdict(list)
for line in p.read_text(encoding='utf-8').splitlines():
 row=json.loads(line);runs[row['runId']].append(row)
assert len(runs)==4
for run,rows in runs.items():
 assert sum(x['event']=='RUN_START' for x in rows)==1,(run,'start')
 assert sum(x['event']=='RUN_END' for x in rows)==1,(run,'end')
 start=next(x['data'] for x in rows if x['event']=='RUN_START');end=rows[-1]['data']
 assert start['options']['budget']==200000 and end['budget']==200000
 progress=[x['data']['expanded'] for x in rows if x['event']=='SEARCH_PROGRESS']
 assert progress==sorted(set(progress)) and all(x%1000==0 for x in progress)
 assert not end['clientExecuted'] and end['actualEndpoint'] is None
 assert end['expanded']<=200000
 if end['status']=='BUDGET_EXHAUSTED':assert end['expanded']==200000 and progress[-1]==200000
 for f in end['loadedFiles']:assert f['sourceExists'] and f['matchesSource'],(run,f)
 summary=next(x['data'] for x in rows if x['event']=='REJECTION_SUMMARY')
 assert all(len(samples)<=3 for samples in summary['samples'].values())
 print(run,end['status'],end['expanded'],round(end['searchMs']/1000,3),'seconds',end['termination'])
print('PASS: four independent starts/ends, exact budget, monotonic progress, bounded samples, source-identical loaded files and no execution claim')
