"""Cuts an English course file into shards for isolated reviewers.

    python -X utf8 .claude/skills/review-english/dump_english.py <course.json> <out folder> [--per-shard N]

Writes shard01.txt, shard02.txt, … each with N topics (default 8; a topic of 20 exercises is a good day's work for a reviewer, eight are too many for one
read at the same care, so the default is a compromise). A topic is shown as the learner meets it: the exercises first as questions (blank, options in a fixed
alphabetical order, no key), then the topic's explanation, then the key and the exercise explanations. The reviewer solves first, then judges.
"""
import json
import re
import sys
from pathlib import Path


def flat(b, out, ind=""):
    t = b.get("type")
    if t == "text":
        out.append(ind + b["text"])
    elif t == "tip":
        out.append(f"{ind}[{b.get('kind')}] {b.get('title', '')}")
        for x in b.get("blocks", []):
            flat(x, out, ind + "  ")
    elif t == "examples":
        out += [ind + "- " + i["text"] for i in b["items"]]
    elif t == "table":
        out += [ind + "| " + " | ".join(r) + " |" for r in b["rows"]]
    else:
        out.append(ind + json.dumps(b, ensure_ascii=False)[:200])


def expl(e):
    out = []
    for b in (e or {}).get("blocks", []):
        flat(b, out)
    return "\n".join(out)


BLANK = re.compile(r"\{\{([^}]*)\}\}(\[\[([^\]]*)\]\])?")


def question(text, kind):
    def repl(m):
        opts = sorted(m.group(1).split("|"), key=str.lower)
        hint = f" (hint: {m.group(3)})" if m.group(3) else ""
        return f"[____ {' / '.join(opts)}]" if kind == "choice" else f"[____]{hint}"
    return BLANK.sub(repl, text)


def key(text, kind):
    ms = BLANK.findall(text)
    if kind == "choice":
        return "; ".join(m[0].split("|")[0] for m in ms)
    return "; ".join(" | ".join(m[0].split("|")) for m in ms)  # gaps: all of them are accepted


def main(path, out, per=8):
    c = json.load(open(path, encoding="utf8"))
    units = c.get("units") or [{"title": "-", "topics": c["topics"]}]
    topics = [(ui, ti, u["title"], t) for ui, u in enumerate(units, 1) for ti, t in enumerate(u["topics"])]
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    for n, i in enumerate(range(0, len(topics), per), 1):
        lines = []
        for ui, ti, utitle, t in topics[i:i + per]:
            ex = t["exercises"]
            lines += [f"################ U{ui}.{ti}  {t['title']}   (unit: {utitle}; {len(ex)} exercises)", "", "--- QUESTIONS (solve these first)"]
            for k, e in enumerate(ex):
                lines.append(f"{k:>2}. [{e['type']}] " + " ".join(question(it['text'], e['type']) for it in e["items"]))
            lines += ["", "--- TOPIC EXPLANATION", expl(t.get("explanation")), "", "--- KEY AND EXERCISE EXPLANATIONS (choice: the one key shown; gaps: every accepted answer)"]
            for k, e in enumerate(ex):
                lines.append(f"{k:>2}. KEY: " + " ".join(key(it['text'], e['type']) for it in e["items"]) + "   || " + expl(e.get("explanation")).replace("\n", " "))
            lines.append("\n")
        (out / f"shard{n:02d}.txt").write_text("\n".join(lines), encoding="utf8")
    print(f"{len(topics)} topics -> {-(-len(topics) // per)} shards in {out}")


if __name__ == "__main__":
    a = sys.argv[1:]
    per = int(a[a.index("--per-shard") + 1]) if "--per-shard" in a else 8
    main(a[0], a[1], per)
