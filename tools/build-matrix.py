"""Serialize builds to avoid Loom cache contention and excessive memory use."""
import json, os, subprocess, sys
from pathlib import Path
root=Path(__file__).resolve().parents[1]
versions=json.loads((root/'versions.json').read_text())
targets=sys.argv[1:] or ['1.21.11']+[v for v in versions if v!='1.21.11']
if any(v not in versions for v in targets):raise SystemExit('Unknown Minecraft target')
out=root/'build';out.mkdir(exist_ok=True)
results={}
for version in targets:
    log=out/('matrix-'+version+'.log')
    with log.open('w') as stream:
        result=subprocess.run([str(root/('gradlew.bat' if os.name=='nt' else 'gradlew')), '--daemon','build','-Ptarget='+version],cwd=root,stdout=stream,stderr=subprocess.STDOUT)
    results[version]={'build':'passed' if result.returncode==0 else 'failed','runtime':'not tested','log':log.name}
    (out/'matrix-results.json').write_text(json.dumps(results,indent=2)+'\n')
    print(version,results[version]['build'],flush=True)
raise SystemExit(1 if any(v['build']!='passed' for v in results.values()) else 0)
