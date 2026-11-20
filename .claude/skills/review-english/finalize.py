"""Makes the rewritten course the bundled one.

    python -X utf8 finalize.py <english-usage-v2.json> <new version number> <repo root>

Sets `version` in the file, writes it as client/src/desktopMain/resources/bundled/english-usage.zip (a zip with course.json) and sets the same version in
manifest.json next to it. The course id stays: the installed app updates its stored course by id and a higher version, keeping progress of what keeps its id.
"""
import json
import sys
import zipfile
from pathlib import Path

src, version, root = Path(sys.argv[1]), int(sys.argv[2]), Path(sys.argv[3])
course = json.load(open(src, encoding="utf8"))
course["version"] = version
json.dump(course, open(src, "w", encoding="utf8"), ensure_ascii=False, indent=1)
bundled = root / "client/src/desktopMain/resources/bundled"
with zipfile.ZipFile(bundled / "english-usage.zip", "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("course.json", json.dumps(course, ensure_ascii=False, indent=1))
manifest = json.load(open(bundled / "manifest.json", encoding="utf8"))
assert manifest["id"] == course["id"], (manifest["id"], course["id"])
manifest["version"] = version
json.dump(manifest, open(bundled / "manifest.json", "w", encoding="utf8"), ensure_ascii=False, indent=1)
print("bundled course", course["id"], "version", version, "topics", sum(len(u["topics"]) for u in course["units"]))
