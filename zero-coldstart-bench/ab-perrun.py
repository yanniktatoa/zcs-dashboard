import json, glob, math, os, sys
def pct(xs, p):
    xs = sorted(xs); i = math.ceil(len(xs) * p) - 1; return xs[max(0, min(i, len(xs) - 1))]
base = os.path.expanduser(sys.argv[1])
for svc in sys.argv[2:]:
    print(svc)
    for v in ("JVM", "NATIVE"):
        row = []
        for f in sorted(glob.glob(f"{base}/{svc}/run_*.json")):
            lat = [r["latencyMs"] for r in json.load(open(f)) if r["variant"] == v and r["success"]]
            row.append(f"{pct(lat, .5):.2f}")
        print(f"  {v:7} p50 par run : {' '.join(row)}")
