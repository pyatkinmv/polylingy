"""Writes a readable text dump of every unit of a course, for reviewers who must not see how the course was made.
Run: python dump_course.py <course.json | course.zip> <output folder>
One file per unit: unit1_dump.txt, unit2_dump.txt, ... A dump has the theory (sections, tables, dialogues, picture descriptions), then the topics with
their exercises, the key marked: the FIRST option of a `{{a|b|c}}` blank is the correct one, ORDER shows the correct order, PAIR the correct match."""
import json
import os
import re
import sys
import zipfile

ann = lambda t: re.sub(r"\[([^\]]*)\]\(([^)]*)\)", r"\1 ‹\2›", t)   # a reading over a word: word ‹reading›
cell = lambda x: ann(x if isinstance(x, str) else x.get("text", ""))


def pic(p):
    return f"[PICTURE: {p.get('alt', '')[:170]}{' | caption: ' + p['caption'] if p.get('caption') else ''}{' | size ' + p['size'] if p.get('size') else ''}]"


def block(b, o, ind=""):
    t = b.get("type")
    if t == "section":
        o.append(f"\n{ind}## THEORY SECTION: {b.get('title', '')}")
        for x in b["blocks"]:
            block(x, o, ind)
    elif t == "text":
        o.append(ind + ann(b["text"]) + ("   (lead)" if b.get("role") == "lead" else ""))
    elif t == "table":
        if b.get("head"):
            o.append(ind + "  | " + " | ".join(b["head"]))
        for r in b["rows"]:
            o.append(ind + "  | " + " | ".join(cell(x) for x in r))
    elif t == "tip":
        o.append(ind + f"TIP ({b.get('kind', 'tip')}): " + " ".join(x.get("text", "") for x in b["blocks"]))
    elif t == "dialogue":
        for l in b["lines"]:
            o.append(ind + f"  {l.get('who', '')}: {ann(l['text'])}  — {l.get('translation', '')}")
    elif t == "image":
        o.append(ind + pic(b))
    elif t == "situation":
        o.append(ind + "[SITUATION" + (f", look {b['look']}" if b.get("look") else "") + "]")
        for x in b["blocks"]:
            block(x, o, ind + "  ")
        if b.get("aside"):
            block(b["aside"], o, ind + "  (beside) ")
    elif t == "track":
        o.append(ind + f"[AUDIO TRACK: {b['title']}]")
    elif t == "video":
        o.append(ind + f"[VIDEO: {b['title']} — {b.get('note', '')}]")
    elif t == "examples":
        for x in b["items"]:
            o.append(ind + "  • " + ann(x["text"]) + (f" — {x['translation']}" if x.get("translation") else ""))
    elif t == "quote":
        o.append(ind + f"“{ann(b['text'])}” {b.get('who', '')}")
    elif t == "words":
        o.append(ind + "  " + " · ".join(b["words"]))
    else:
        o.append(ind + f"<{t}>")


def exercise(i, e, o):
    flags = []
    if e.get("aside"):
        flags.append("PICTURE beside: " + e["aside"]["image"]["alt"][:150])
    if e.get("audio"):
        flags.append("AUDIO of the exercise")
    if e.get("video"):
        flags.append("VIDEO shown")
    look = e.get("look")
    if look:
        flags.append("LOOK " + look["type"] + ": " + " / ".join(str(v) for k, v in look.items() if k != "type"))
    o.append(f"-- exercise {i} [{e['type']}] {e.get('instruction', '')}  {' | '.join(flags)}")
    if e.get("head"):
        o.append("     TABLE HEAD: " + " | ".join(e["head"]))
    for r in e.get("rows", []):
        o.append("     TABLE ROW (the {{…}} cell is typed by the learner): " + " | ".join(str(x) for x in r))
    for g in e.get("groups", []):
        o.append(f"     SORT GROUP «{g['title']}»: " + ", ".join(g["items"]))
    for it in e.get("items", []):
        if it.get("image"):
            o.append("     [PICTURE of the item: " + it["image"].get("alt", "")[:120] + "]")
        if "text" in it:
            o.append("     " + ann(it["text"]).replace("\n", " / ") + (("   OPTIONS: " + " | ".join(it["options"])) if it.get("options") else ""))
        if it.get("parts"):
            o.append("     ORDER (correct order): " + " ‖ ".join(ann(x) for x in it["parts"]))
        if it.get("answer"):
            o.append("     ORDER (correct order): " + it["answer"] + (f"   [{ann(it['note'])}]" if it.get("note") else ""))
        if it.get("words"):
            o.append("     ORDER (correct order): " + " ".join(it["words"]))
    for p in e.get("pairs", []):
        o.append("     PAIR: " + "  ↔  ".join(x if isinstance(x, str) else "[PICTURE: " + x["image"]["alt"][:90] + "]" for x in p))


def expl(where, ex, o):
    o.append(f"   EXPLANATION shown after the check ({where}):")
    tmp = []
    for b in ex.get("blocks", []):
        if b.get("type") == "seeAlso":
            tmp.append("     (link to a theory section)")
        else:
            block(b, tmp, "     ")
    o.extend(tmp)


def main(path, out):
    text = zipfile.ZipFile(path).read("course.json").decode("utf8") if path.endswith(".zip") else open(path, encoding="utf8").read()
    course = json.loads(text)
    os.makedirs(out, exist_ok=True)
    for n, u in enumerate(course["units"], 1):
        o = [f"COURSE: {course['name']} — UNIT {n}: {u['title']}", "TOPIC ORDER: " + " → ".join(t["title"] for t in u["topics"])]
        for b in (u.get("theory") or {}).get("blocks", []):
            block(b, o)
        for t in u["topics"]:
            o.append(f"\n#### TOPIC: {t['title']}")
            for i, e in enumerate(t["exercises"], 1):
                exercise(i, e, o)
                if e.get("explanation"):
                    expl("exercise " + str(i), e["explanation"], o)
            if t.get("explanation"):
                expl("topic", t["explanation"], o)
        with open(os.path.join(out, f"unit{n}_dump.txt"), "w", encoding="utf8") as f:
            f.write("\n".join(o))
        print(f"unit{n}_dump.txt", len(o), "lines")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
