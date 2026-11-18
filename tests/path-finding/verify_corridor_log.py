import collections
import json
from pathlib import Path

root = Path("build/path-audit")
rows = [json.loads(line) for line in (root / "corridor-pathfinding.jsonl").read_text(encoding="utf-8").splitlines()]
runs = collections.defaultdict(list)
for row in rows:
    runs[row["runId"]].append(row)
assert len(runs) == 6
for run, events in runs.items():
    starts = [x["data"] for x in events if x["event"] == "RUN_START"]
    ends = [x["data"] for x in events if x["event"] == "RUN_END"]
    assert len(starts) == len(ends) == 1, run
    start, end = starts[0], ends[0]
    assert start["options"]["budget"] == end["budget"] == 200000
    assert start["startSupported"] and start["targetSupported"]
    assert all(f["matchesSource"] for f in end["loadedFiles"]), run
    if run.startswith("D1"):
        assert start["requested"] == {"x": 35.0, "y": 12.0, "z": 17.0}
        assert any(f["loadedPath"].endswith("Deep Caverns 0 0.litematic") for f in end["loadedFiles"])
    assert end["expanded"] <= end["budget"]
    trial = max((x.get("clientTrial", 1) for x in events if x["event"] == "CLIENT_END"), default=0)
    live = [x for x in events if x.get("clientTrial", 1) == trial]
    actual_ends = [x["data"] for x in live if x["event"] == "CLIENT_END"]
    for item in [x["data"] for x in live if x["event"] == "CAST"]:
        assert 0 < item["distance"] <= 12 and (item["intervalMs"] < 0 or item["intervalMs"] + .01 >= item["cooldownMs"])
    actual = actual_ends[-1] if actual_ends else {"status":"NOT_RUN"}
    if actual["status"] == "ARRIVED":
        start_live = next(x["data"] for x in live if x["event"] == "CLIENT_START")
        assert start_live["yaw"] == start_live["pitch"] == 0
        assert actual["actualEndpoint"] == end["result"]["resolved"]
        confirmations = [x["data"] for x in live if x["event"] == "POSITION_CONFIRMATION"]
        assert len(confirmations) == actual["casts"] == actual["confirmations"]
        assert all(x["actual"] == x["predicted"] for x in confirmations)
    print(run, end["status"], end["expanded"], f'{end["searchMs"]/1000:.3f}s', actual)
print("PASS: all planning starts/ends, corrected D1, source-identical map files and measured cooldown bounds")
walking = [x["data"] for x in rows if x["event"] == "WALK_SMOKE_CLIENT_END"]
assert walking and walking[-1]["status"] == "ARRIVED" and walking[-1]["casts"] == walking[-1]["confirmations"] == 0
print("PASS: physical walking with transmission disabled and zero simulated teleport commands")
