"""Mechanical checks of an English course file (course.json): what a script can find before any reviewer reads a line.

    python -X utf8 .claude/skills/review-english/check_english.py <course.json> [report.md]

Finds: a glued ending that misspells the word, stray quotes, leftovers of the generator talking to itself, explanations that admit a second answer,
claims with absolutes, doubled sentences. Every finding is a *candidate*: a person (or a reviewer) decides; nothing here edits the course.
"""
import collections
import json
import re
import sys


def flat(b, out):
    t = b.get("type")
    if t == "text":
        out.append(b["text"])
    elif t == "tip":
        out.append(f"[{b.get('kind')}] {b.get('title', '')}")
        for x in b.get("blocks", []):
            flat(x, out)
    elif t == "examples":
        out += [i["text"] for i in b["items"]]
    elif t == "table":
        out += [" | ".join(r) for r in b["rows"]]
    else:
        out.append(json.dumps(b, ensure_ascii=False))


def expl(e):
    out = []
    for b in (e or {}).get("blocks", []):
        flat(b, out)
    return "\n".join(out)


BLANK = re.compile(r"\{\{([^}]*)\}\}(?:\[\[[^\]]*\]\])?([A-Za-z]+)?")
DRAFT = re.compile(r"Let'?s (rephrase|re-?write|try)|Trick question|\(Note:|Wait,|Actually,|rephrase|I think|Hmm|TODO", re.I)
SOURCE = re.compile(r"rated \d/4|rating \d/4|\bthis exercise\b|\bthis quiz\b|the quiz\b|\bflashcard\b|\bcard\b", re.I)
SECOND = re.compile(r"also (possible|correct|acceptable|works|fine|used)|both (are|would|work|fit|could)|could (also|equally)|equally (valid|correct|good)|either (one|word)|interchangeable|can be used here too", re.I)
ABSOLUTE = re.compile(r"\b(always|never|no other (word|verb|option)|only (correct|possible|option)|cannot be used|must be)\b", re.I)
STEREO = re.compile(r"\b(psychologists|lawyers|engineers|philosophers|doctors|scientists|journalists|economists)\b[^.]{0,40}\b(always|never|usually|say|use|discuss)\b", re.I)
VOWELS = "aeiou"


def glued_problem(key, s):
    """The word the learner sees after the key is typed into the blank and the glued ending: is it spelled right?"""
    word = key.split()[-1]
    if s in ("ed", "d") and word.endswith("e") and s == "ed":
        return f"{key}+{s}: '{word}e'+'ed' doubles the e"
    if s == "s" and re.search(r"(s|x|z|ch|sh)$", word):
        return f"{key}+s: needs -es"
    if s in ("s", "ed") and re.search(r"[^aeiou]y$", word):
        return f"{key}+{s}: y becomes i"
    if s in ("ed", "ing", "er", "est") and re.search(r"^[^aeiou]*[aeiou][^aeiouwxy]$", word) and len(word) <= 5:
        return f"{key}+{s}: one-syllable word may need a doubled consonant"
    if s in ("ing",) and word.endswith("e") and not word.endswith("ee"):
        return f"{key}+ing: drops the e"
    if s == "s" and key.endswith("s") and len(key) > 3:
        return f"{key}+s: the key already ends in s"
    return None


def main(path, report=None):
    c = json.load(open(path, encoding="utf8"))
    units = c.get("units") or [{"title": "-", "topics": c["topics"]}]
    found = collections.OrderedDict()
    seen = collections.defaultdict(list)

    def add(kind, where, text):
        found.setdefault(kind, []).append(f"{where} :: {text}")

    total = 0
    for ui, u in enumerate(units, 1):
        for ti, t in enumerate(u["topics"]):
            tw = f"U{ui}.{ti} {t['title']}"
            if '"' in t["title"]:
                add("stray quote in a title", tw, t["title"])
            ttext = expl(t.get("explanation"))
            for k, rx, name in ((DRAFT, DRAFT, "generator's draft left in a topic explanation"), (SOURCE, SOURCE, "trace of the source in a topic explanation"),
                                (ABSOLUTE, ABSOLUTE, "absolute in a topic explanation (check it is true)"), (STEREO, STEREO, "'professionals always say…' (often invented)")):
                m = rx.search(ttext)
                if m:
                    add(name, tw, "…" + ttext[max(0, m.start() - 50):m.end() + 60].replace("\n", " ") + "…")
            for ei, e in enumerate(t["exercises"]):
                total += 1
                ew = f"{tw} #{ei}"
                et = expl(e.get("explanation"))
                text = " / ".join(i["text"] for i in e.get("items", []))
                for rx, name in ((DRAFT, "generator's draft left in an exercise explanation"), (SOURCE, "trace of the source in an exercise explanation"),
                                 (SECOND, "explanation admits a second answer"), (STEREO, "'professionals always say…' (often invented)")):
                    m = rx.search(et)
                    if m:
                        add(name, ew, f"{text[:90]} || …{et[max(0, m.start() - 40):m.end() + 60]}…".replace("\n", " "))
                for it in e.get("items", []):
                    seen[re.sub(r"\W+", " ", re.sub(r"\{\{[^}]*\}\}", "_", it["text"])).lower().strip()].append(ew)
                    if '"' in it["text"]:
                        add("stray quote in an item", ew, it["text"][:100])
                    if e["type"] == "gaps" and "[[" not in it["text"] and "{{" in it["text"] and not e.get("wordBank"):
                        add("gaps without a hint", ew, it["text"][:100])
                    for am in re.finditer(r"\b(a|an)\s+\{\{([^}]*)\}\}", it["text"], re.I):
                        o = am.group(2).split("|")
                        if e["type"] == "choice" and len({x[0].lower() in VOWELS for x in o}) > 1:
                            add("the article before the blank excludes some options", ew, it["text"][:100])
                    for m in BLANK.finditer(it["text"]):
                        opts = m.group(1).split("|")
                        s = m.group(2)
                        if len(set(opts)) != len(opts):
                            add("repeated option", ew, it["text"][:100])
                        if e["type"] == "choice" and len(opts) < 2:
                            add("choice with one option", ew, it["text"][:100])
                        if s:
                            p = glued_problem(opts[0], s)
                            if p:
                                add("glued ending may misspell the word", ew, f"{p} :: {it['text'][:100]}")
                            # the other options with the same ending are shown to the learner too
                        if e["type"] == "choice" and re.search(r"\b" + re.escape(opts[0]) + r"\b", re.sub(r"\{\{[^}]*\}\}", "", it["text"]), re.I):
                            add("the key also stands outside the blank", ew, it["text"][:100])
    for sent, wheres in seen.items():
        if len(wheres) > 1 and len(sent) > 25:
            add("the same sentence twice", ", ".join(wheres[:3]), sent[:100])

    lines = [f"# Mechanical checks of {path}", "", f"{total} exercises.", "", "| Finding | Count |", "|---|---|"]
    lines += [f"| {k} | {len(v)} |" for k, v in found.items()]
    for k, v in found.items():
        lines += ["", f"## {k} ({len(v)})", ""] + [f"- {x}" for x in v[:400]]
    out = "\n".join(lines)
    if report:
        open(report, "w", encoding="utf8").write(out)
    print("\n".join(lines[:4 + 2 + len(found)]))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
