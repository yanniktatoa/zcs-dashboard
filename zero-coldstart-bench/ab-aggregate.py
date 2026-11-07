import json, glob, sys, math, statistics as st
def pct(xs, p):
    xs = sorted(xs); i = math.ceil(len(xs) * p) - 1
    return xs[max(0, min(i, len(xs) - 1))]
runs = [json.load(open(f)) for f in sorted(glob.glob(sys.argv[1] + "/run_*.json"))]
print(f"{len(runs)} runs\n{'variante':8} {'n/run':>6} {'erreurs':>7} | {'p50 (min-max)':>22} | {'p95 (min-max)':>22} | {'p99 (min-max)':>22} | poolé p50/p95/p99")
out = {}
for v in ("JVM", "NATIVE"):
    per, pooled, err, n = [], [], 0, 0
    for run in runs:
        rows = [r for r in run if r["variant"] == v]
        lat = [r["latencyMs"] for r in rows if r["success"]]
        err += len(rows) - len(lat); n = len(rows)
        if not lat:
            print(f"  ! {v} : 0 requete reussie (statuts : {sorted({r['statusCode'] for r in rows})})"); continue
        per.append((pct(lat, .5), pct(lat, .95), pct(lat, .99))); pooled += lat
    if not per: continue
    cols = list(zip(*per))
    fmt = lambda c: f"{st.median(c):7.2f} ({min(c):.2f}-{max(c):.2f})"
    print(f"{v:8} {n:6} {err:7} | {fmt(cols[0]):>22} | {fmt(cols[1]):>22} | {fmt(cols[2]):>22} | {pct(pooled,.5):.2f}/{pct(pooled,.95):.2f}/{pct(pooled,.99):.2f}")
    out[v] = [st.median(c) for c in cols]
if len(out) == 2: print("\nratio Native/JVM (mediane des runs) : p50 %.2f  p95 %.2f  p99 %.2f" % tuple(out["NATIVE"][i] / out["JVM"][i] for i in range(3)))
