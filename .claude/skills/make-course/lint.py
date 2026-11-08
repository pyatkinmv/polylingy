"""Finds sentences in a course file that talk about the source instead of the language.
Run: python .claude/skills/make-course/lint.py course.json|course.zip
Prints every text field with a forbidden word; exit code 1 when there is any. `alt` (picture descriptions) and URLs are not read."""
import json
import re
import sys
import zipfile

FORBIDDEN = [
    r"книг", r"учебник", r"тетрад", r"Activity", r"Mastering", r"Wightwick", r"оригинал", r"перенес", r"не перенес",
    r"сайт", r"ресурс", r"материал", r"презентац", r"PowerPoint", r"расшифровк", r"transcript", r"worksheet", r"лист[ыа]? заданий",
    r"Memrise", r"карточк", r"страниц", r"стр\.", r"\bunit\s*\d", r"юнит(е|а|у|ов)?\s*\d", r"в приложени", r"прописи",
    r"Grammar", r"грамматик[аи] серии", r"серии",
]
RX = re.compile("|".join(FORBIDDEN), re.I)
SKIP_KEYS = {"alt", "url", "src", "audio", "id", "type", "format"}


def walk(x, path, out):
    if isinstance(x, str):
        if RX.search(x) and not re.fullmatch(r"Юнит \d+", x):
            out.append((path, x))
    elif isinstance(x, list):
        for i, v in enumerate(x):
            walk(v, f"{path}[{i}]", out)
    elif isinstance(x, dict):
        for k, v in x.items():
            if k not in SKIP_KEYS:
                walk(v, f"{path}.{k}", out)


def main(p):
    if p.endswith(".zip"):
        text = zipfile.ZipFile(p).read("course.json").decode("utf8")
    else:
        text = open(p, encoding="utf8").read()
    out = []
    walk(json.loads(text), "$", out)
    for path, t in out:
        print(path, "|", t[:140].replace("\n", " "))
    print(len(out), "suspicious text(s)")
    return 1 if out else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
