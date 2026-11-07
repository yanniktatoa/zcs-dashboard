import csv, glob, os, statistics as st, sys
d = os.path.expanduser(sys.argv[1]); mode = sys.argv[2]
def rows(path):
    return list(csv.DictReader(open(path))) if os.path.exists(path) else []
print(f"## {mode.upper()} - {d}")
trials = rows(f"{d}/startup/startup-trials-{mode}.csv")
print("service | n | startup appli (s) med [min-max] | cold start appli prete (s) | pod Ready (s)")
for svc in ("catalog-service", "user-service", "loan-service", "library-gateway"):
    r = [x for x in trials if x["service"] == svc]
    if not r:
        print(f"{svc} | aucun essai"); continue
    def f(col):
        v = [float(x[col]) for x in r]
        return f"{st.median(v):.2f} [{min(v):.2f}-{max(v):.2f}]"
    print(f"{svc} | {len(r)} | {f('process_running_s')} | {f('app_up_s')} | {f('ready_s')}")
samples = rows(f"{d}/memory/memory-samples-{mode}.csv")
print("\nmemoire au repos (mediane des releves) :")
for x in rows(f"{d}/memory/memory-{mode}.csv"):
    n = sum(1 for s in samples if s["service"] == x["service"])
    print(f"  {x['service']}: {x['memory_mi']} Mi, {x['cpu_cores']} m ({n} releves)")
print("\ntailles d'image :")
for p in sorted(glob.glob(f"{d}/image*/*")):
    print(f"  [{os.path.basename(p)}]"); print(open(p).read().rstrip())
