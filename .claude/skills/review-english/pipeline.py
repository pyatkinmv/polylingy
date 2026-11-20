"""Bookkeeping of the rewrite of the English course, topic by topic.

    python -X utf8 pipeline.py extract  <course.json> <workdir> <unit> [<from> <to>]   write src_U<unit>_<topic>.json (topic indexes from..to-1, 0-based)
    python -X utf8 pipeline.py assemble <course.json> <workdir> <out.json> [<wave.json>] put every new_U<u>_<t>.json into a copy of the course; the topic keeps its id, its exercises get
                                                                                           stable ids; the optional wave.json holds only the rewritten topics (for the dump and the review)
    python -X utf8 pipeline.py status   <course.json> <workdir>                             what is written, per unit

Labels are as in the reports: U<unit, 1-based>.<topic, 0-based>.
"""
import json
import re
import sys
import uuid
from pathlib import Path


def load(p):
    return json.load(open(p, encoding="utf8"))


def new_files(work):
    for f in sorted(Path(work).glob("new_U*_*.json")):
        m = re.fullmatch(r"new_U(\d+)_(\d+)\.json", f.name)
        if m:
            yield int(m.group(1)), int(m.group(2)), f


def extract(course, work, unit, a=None, b=None):
    c = load(course)
    topics = c["units"][unit - 1]["topics"]
    Path(work).mkdir(parents=True, exist_ok=True)
    for ti in range(a if a is not None else 0, b if b is not None else len(topics)):
        json.dump(topics[ti], open(Path(work) / f"src_U{unit}_{ti}.json", "w", encoding="utf8"), ensure_ascii=False, indent=1)
        print(f"U{unit}.{ti} {topics[ti]['title']} ({len(topics[ti]['exercises'])})")


def assemble(course, work, out, wave=None):
    c = load(course)
    done = []
    for u, t, f in new_files(work):
        new = load(f)
        old = c["units"][u - 1]["topics"][t]
        new["id"] = old["id"]
        for k, e in enumerate(new["exercises"]):
            e["id"] = str(uuid.uuid5(uuid.UUID(old["id"]), json.dumps(e["items"], ensure_ascii=False)))
        c["units"][u - 1]["topics"][t] = new
        done.append((u, t, new))
    json.dump(c, open(out, "w", encoding="utf8"), ensure_ascii=False, indent=1)
    if wave:
        w = {k: v for k, v in c.items() if k != "units"}
        w["units"] = [{"title": "Wave", "topics": [t for _, _, t in done]}]
        w["name"] = "wave"
        json.dump(w, open(wave, "w", encoding="utf8"), ensure_ascii=False, indent=1)
    print(f"{len(done)} topics rewritten -> {out}")


def status(course, work):
    c = load(course)
    have = {(u, t) for u, t, _ in new_files(work)}
    for ui, u in enumerate(c["units"], 1):
        n = len(u["topics"])
        d = sum(1 for t in range(n) if (ui, t) in have)
        print(f"U{ui} {u['title'][:40]:40} {d}/{n}")
    print("total", len(have), "of", sum(len(u["topics"]) for u in c["units"]))


if __name__ == "__main__":
    cmd, a = sys.argv[1], sys.argv[2:]
    if cmd == "extract":
        extract(a[0], a[1], int(a[2]), *(int(x) for x in a[3:5]))
    elif cmd == "assemble":
        assemble(a[0], a[1], a[2], a[3] if len(a) > 3 else None)
    elif cmd == "status":
        status(a[0], a[1])
