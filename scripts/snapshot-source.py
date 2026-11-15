#!/usr/bin/env python3
"""Record reproducible source identities without reading local credentials/configuration."""
import hashlib
import json
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[1]
if len(sys.argv) != 2:
    raise SystemExit("usage: snapshot-source.py verification/runs/<run>/source.json")
output = (root / sys.argv[1]).resolve()
if not output.is_relative_to(root / "verification" / "runs"):
    raise SystemExit("output must be inside verification/runs")
allowed_suffixes = {".kt", ".java", ".xml", ".gradle", ".json", ".py", ".sh", ".md"}
excluded_parts = {"build", ".gradle", ".git", ".test-runtime", "runs", "evidence", "node_modules"}
files = []
for path in sorted(root.rglob("*")):
    relative = path.relative_to(root)
    if any(part in excluded_parts for part in relative.parts):
        continue
    if path.is_symlink() or not path.is_file():
        continue
    if path.name.startswith(".env") or ".env" in path.suffixes:
        continue
    if path.suffix not in allowed_suffixes:
        continue
    files.append({"path": relative.as_posix(), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
identity = hashlib.sha256(json.dumps(files, sort_keys=True).encode()).hexdigest()
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps({"schemaVersion": 1, "treeSha256": identity, "files": files,
    "scope": "Allowlisted project source/docs/tests. Excludes credentials, local properties, build outputs and run evidence."}, indent=2) + "\n")
print(identity)
