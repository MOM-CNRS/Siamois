#!/usr/bin/env python3
"""
Régénère src/main/resources/thesaurus-system-concepts.properties d'après un RDF du thésaurus système.

    python3 -I build_system_concepts.py --rdf siamois-socle-commun-v2.rdf [--previous-rdf siamois-socle-commun.rdf]

Les clés viennent des notations du RDF : `SIAMOIS#<code>` (champ à vocabulaire) et `SIAMOISFIELD#<table>.<propriété>`
(champ simple). Les lignes `legacy.<TABLE>.<ancien id>` relient un ancien identifiant (th230) au concept actuel :
  - un ancien identifiant numérique est un concept d'une version précédente du thésaurus (--previous-rdf) : on le suit
    jusqu'à sa clé, puis jusqu'au concept actuel (alias de clés renommées dans ALIASES) ; sans équivalent, la ligne disparaît ;
  - un identifiant symbolique (`study.reportCode`) vaut le concept `TABLE.reportCode` s'il existe maintenant, sinon lui-même.
"""
import argparse
import os
import re
import sys
from html import unescape

HERE = os.path.dirname(os.path.abspath(__file__))
PROPS = os.path.join(HERE, "..", "..", "src", "main", "resources", "thesaurus-system-concepts.properties")

# Clés renommées entre deux versions du thésaurus : ancienne -> nouvelle.
ALIASES = {"SIAS.preservationCondition": "SIAS.PRESERVATIONCONDITION", "SIAS.sanitaryAlert": "SIAS.SANITARYALERT",
           "SIAST.TYPETRAIT": "SIAST.TYPE"}

ap = argparse.ArgumentParser()
ap.add_argument("--rdf", default=os.path.join(HERE, "siamois-socle-commun.rdf"))
ap.add_argument("--previous-rdf", help="version précédente du thésaurus, pour suivre les anciens identifiants numériques")
ap.add_argument("--properties", default=PROPS)
ap.add_argument("--thesaurus", default="th332", help="identifiant du thésaurus système, pour l'en-tête")
args = ap.parse_args()


def keys_of(path):
    text = open(path, encoding="utf8").read()
    out = {}
    for about, body in re.findall(r'<rdf:Description rdf:about="([^"]*)">(.*?)</rdf:Description>', text, re.S):
        m = re.search(r"idc=(\d+)", about)
        if not m or 'Concept"' not in body:
            continue
        for n in re.findall(r"<skos:notation>([^<]*)", body):
            n = unescape(n)
            if n.startswith("SIAMOIS#") and "." in n:
                out[n[8:]] = m.group(1)
            elif n.startswith("SIAMOISFIELD#"):
                out[n[13:]] = m.group(1)
    return out


current = keys_of(args.rdf)
previous = keys_of(args.previous_rdf) if args.previous_rdf else {}
previous_key = {v: k for k, v in previous.items()}

old_lines = [l.rstrip("\n") for l in open(args.properties, encoding="utf8")] if os.path.exists(args.properties) else []
legacy = [l.split("=", 1) for l in old_lines if l.startswith("legacy.") and "=" in l]

out = ["# Concepts des champs système, dans le thésaurus système (%s) : clé de champ = table.propriété ou table.CODE." % args.thesaurus,
       "# Généré à partir de docs/thesaurus-socle/%s par build_system_concepts.py." % os.path.basename(args.rdf)]
for key, cid in current.items():
    out.append("%s=%s" % (key, cid))
# Anciennes clés renommées : elles restent lisibles par leur ancien nom (un champ est retrouvé par table.propriété).
for old, new in ALIASES.items():
    if old not in current and new in current:
        out.append("%s=%s" % (old, current[new]))

dropped = []
for k, v in legacy:
    table = k.split(".")[1]
    if v.isdigit():
        key = previous_key.get(v)
        key = ALIASES.get(key, key)
        if key in current:
            out.append("%s=%s" % (k, current[key]))
        else:
            dropped.append((k, key))
    else:
        prop = v.split(".")[-1]
        out.append("%s=%s" % (k, current.get("%s.%s" % (table, prop), v)))

open(args.properties, "w", encoding="utf8").write("\n".join(out) + "\n")
print("%d clés, %d lignes legacy, %d sans équivalent" % (len(current), len(legacy) - len(dropped), len(dropped)))
for k, key in dropped:
    print("  supprimée :", k, "(", key, ")")
