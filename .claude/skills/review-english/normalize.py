"""Fixes the shape slips writers make, in every new_U*_*.json of a work folder: `examples` items written as plain strings.

    python -X utf8 normalize.py <workdir>
"""
import json
import sys
from pathlib import Path


def fix(node, counter):
    if isinstance(node, dict):
        if node.get("type") == "examples" and isinstance(node.get("items"), list):
            new = []
            for it in node["items"]:
                if isinstance(it, str):
                    new.append({"text": it})
                    counter[0] += 1
                else:
                    new.append(it)
            node["items"] = new
        for v in node.values():
            fix(v, counter)
    elif isinstance(node, list):
        for v in node:
            fix(v, counter)


total = 0
for f in sorted(Path(sys.argv[1]).glob("new_U*_*.json")):
    d = json.load(open(f, encoding="utf8"))
    c = [0]
    fix(d, c)
    if c[0]:
        json.dump(d, open(f, "w", encoding="utf8"), ensure_ascii=False, indent=1)
        print(f.name, c[0])
        total += c[0]
print("fixed items:", total)
