"""What the learner sees, as text: every exercise of a course the way the app shows it, WITHOUT the key.
Run: python student_view.py <course.json | course.zip> <output folder>   -> unit1_student.txt, unit2_student.txt, ...
Rules of the view: a blank {{answer}} is [____]; a blank with inline options {{a|b|c}} is [a | b | c] (the app shows pills, shuffled);
`choice` with an `options` list shows the options in brackets; match shows the left column in order and the right column scrambled (sorted);
order shows the tiles scrambled (sorted); sort shows the items scrambled and the group titles; fix shows each marked word with a field for the correction.
Use it (1) to solve the exercises yourself before reading the key and (2) as the input of the solvability check in sanity.py."""
import json
import os
import re
import sys
import zipfile

BLANK = re.compile(r"\{\{([^{}]*)\}\}")
ann = lambda t: re.sub(r"\[([^\]]*)\]\(([^)]*)\)", r"\1 ‹\2›", t)


def blanks_to_view(text, options=None, fix=False):
    """Replace blanks by what the learner sees."""
    def rep(m):
        body = m.group(1)
        if fix or "=>" in body:                # fix: a marked word, wrong or right (the learner does not know which)
            shown = body.split("=>")[0].strip()
            return f"⟦{shown} ✓ | → ____⟧"
        if "|" in body:
            return "[" + " | ".join(sorted(x.strip() for x in body.split("|"))) + "]"
        if options is not None:
            return "[" + " | ".join(sorted(options)) + "]"
        return "[____]"
    out = BLANK.sub(rep, ann(text))
    return out.replace("\n", " / ")


def view_exercise(e):
    t = e["type"]
    lines = [f"-- [{t}] {e.get('instruction', '')}"]
    if e.get("aside"):
        lines.append("   (picture beside: " + e["aside"]["image"].get("alt", "")[:100] + ")")
    for it in e.get("items", []):
        pic = f"(picture: {it['image'].get('alt', '')[:70]}) " if it.get("image") else ""
        if t == "order":
            tiles = it.get("parts") or it.get("words") or (it.get("answer", "").split())
            lines.append("   tiles (scrambled): " + " | ".join(sorted(tiles, key=str.lower)) + (f"   [hint: {ann(it['note'])}]" if it.get("note") else ""))
        elif "text" in it:
            lines.append("   " + pic + blanks_to_view(it["text"], it.get("options"), fix=(t == "fix")))
    if t == "match":
        pairs = e.get("pairs", [])
        side = lambda x: x if isinstance(x, str) else "[picture: " + x["image"].get("alt", "")[:50] + "]"
        lines.append("   left (in order): " + " ; ".join(side(p[0]) for p in pairs))
        lines.append("   right (scrambled): " + " ; ".join(sorted((side(p[1]) for p in pairs), key=str.lower)))
    if t == "sort":
        groups = e.get("groups", [])
        lines.append("   groups: " + " | ".join(g["title"] for g in groups))
        lines.append("   items (scrambled): " + " ; ".join(sorted((x for g in groups for x in g["items"]), key=str.lower)))
    if t == "table":
        if e.get("head"):
            lines.append("   head: " + " | ".join(map(str, e["head"])))
        for r in e.get("rows", []):
            lines.append("   row: " + " | ".join(blanks_to_view(str(c) if isinstance(c, str) else c.get("text", "")) for c in r))
    if e.get("wordBank"):
        lines.append("   word bank: " + " ; ".join(sorted(e["wordBank"])))
    return lines


def main(path, out):
    text = zipfile.ZipFile(path).read("course.json").decode("utf8") if path.endswith(".zip") else open(path, encoding="utf8").read()
    course = json.loads(text)
    os.makedirs(out, exist_ok=True)
    for n, u in enumerate(course["units"], 1):
        o = [f"UNIT {n}: {u['title']}  (what the learner sees; no key)"]
        for t in u["topics"]:
            o.append(f"\n#### TOPIC: {t['title']}")
            for e in t["exercises"]:
                o.extend(view_exercise(e))
        with open(os.path.join(out, f"unit{n}_student.txt"), "w", encoding="utf8") as f:
            f.write("\n".join(o))
    print(len(course["units"]), "student views written")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
