"""Dump the rewritten topics of one batch for a reviewer: python -X utf8 batch_dump.py <workdir> <batch number> -> <workdir>/rev_B<k>.txt

The batch is taken from <workdir>/batches.json; the topics are the files new_U<u>_<t>.json. The text is the same as dump_english.py makes (questions, explanation, key).
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import dump_english as d

work = Path(sys.argv[1])
k = int(sys.argv[2])
batch = json.load(open(work / "batches.json", encoding="utf8"))[k - 1]
topics = []
for b in batch:
    f = work / f"new_U{b['u']}_{b['t']}.json"
    t = json.load(open(f, encoding="utf8"))
    topics.append((b["u"], b["t"], "unit " + str(b["u"]), t))
lines = []
for ui, ti, utitle, t in topics:
    ex = t["exercises"]
    lines += [f"################ U{ui}.{ti}  {t['title']}   ({len(ex)} exercises)", "", "--- QUESTIONS (solve these first)"]
    for n, e in enumerate(ex):
        lines.append(f"{n:>2}. [{e['type']}] " + " ".join(d.question(it["text"], e["type"]) for it in e["items"]))
    lines += ["", "--- TOPIC EXPLANATION", d.expl(t.get("explanation")), "", "--- KEY AND EXERCISE EXPLANATIONS (choice: the one key shown; gaps: every accepted answer)"]
    for n, e in enumerate(ex):
        lines.append(f"{n:>2}. KEY: " + " ".join(d.key(it["text"], e["type"]) for it in e["items"]) + "   || " + d.expl(e.get("explanation")).replace("\n", " "))
    lines.append("\n")
out = work / f"rev_B{k}.txt"
out.write_text("\n".join(lines), encoding="utf8")
print(out, len(topics), "topics")
