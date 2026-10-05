"""Prüft die Sprachdateien: Fehlt in einer Sprache ein Text, den der Code verwendet, oder ein
Platzhalter (%1$s, %d …)? Gibt es Texte, die der Code nicht mehr verwendet?

Aufruf: python store/check_strings.py
"""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src")
RES = os.path.join(ROOT, "main", "res")


def load(path):
    root = ET.parse(path).getroot()
    out = {}
    for el in root:
        name = el.get("name")
        if el.tag == "string":
            out[("string", name)] = "".join(el.itertext())
        elif el.tag == "plurals":
            out[("plurals", name)] = " | ".join("".join(i.itertext()) for i in el)
        elif el.tag == "string-array":
            out[("array", name)] = len(list(el))
    return out


def placeholders(text):
    return sorted(set(re.findall(r"%(?:\d\$)?[sd]", str(text))))


code = ""
for f in glob.glob(os.path.join(ROOT, "**", "*.kt"), recursive=True):
    code += open(f, encoding="utf-8").read()
used = {(kind, name) for kind, name in re.findall(r"R\.(string|plurals|array)\.(\w+)", code)}

base = load(os.path.join(RES, "values", "strings.xml"))
problems = 0
for key in sorted(used - set(base)):
    print(f"FEHLT in values/: {key}")
    problems += 1
for key in sorted(set(base) - used - {("string", "app_name")}):
    print(f"Unbenutzt: {key}")

for path in sorted(glob.glob(os.path.join(RES, "values-*", "strings.xml"))):
    lang = os.path.basename(os.path.dirname(path))
    other = load(path)
    for key in sorted(set(base) - set(other) - {("string", "app_name")}):
        print(f"{lang}: fehlt {key}")
        problems += 1
    for key in sorted(set(other) - set(base)):
        print(f"{lang}: überzählig {key}")
        problems += 1
    for key, text in other.items():
        if key in base and key[0] != "array" and placeholders(text) != placeholders(base[key]):
            # Bei Mengenangaben darf die Einzahl ohne Zahl auskommen; dann nur die anderen Formen prüfen.
            print(f"{lang}: Platzhalter weichen ab bei {key}: {placeholders(text)} statt {placeholders(base[key])}")
            problems += 1
        if key[0] == "array" and text != base[key]:
            print(f"{lang}: {key} hat {text} statt {base[key]} Einträge")
            problems += 1

print("OK" if problems == 0 else f"{problems} Probleme")
sys.exit(1 if problems else 0)
