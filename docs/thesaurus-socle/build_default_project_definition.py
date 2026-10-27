#!/usr/bin/env python3
"""
Met à jour src/main/resources/default-project/definition.json d'après un RDF du thésaurus socle.

    python3 -I build_default_project_definition.py --rdf EXPORT.rdf [--thesaurus-url https://thesaurus.mom.fr/?idt=thNNN]

Le RDF à passer est de préférence l'EXPORT d'OpenTheso du thésaurus une fois importé : OpenTheso peut réattribuer les
identifiants de concepts et de collections, et la définition doit porter ceux de l'instance réelle. Le RDF produit par
generate_thesaurus_socle.py convient si les identifiants n'ont pas changé.

Le script garde les tables, les codes de type, les libellés et les formats d'identifiant du fichier existant, et recalcule :
  - l'identifiant du concept de chaque type (retrouvé par son libellé sous le champ type de la table) ;
  - pour chaque type, les collections « Table · Champ — Type  [CODE @ CODE_TYPE:slug] » qui lui sont propres
    (`lists`: code de champ -> identifiant de collection, `listNames`: code de champ -> nom exact de la collection).
Pour les tables de --all-types (DOCUMENT par défaut), les types sont tous ceux que le thésaurus déclare.
Il s'arrête si un type de la définition n'existe plus dans le thésaurus.
"""
import argparse
import os
import re
import sys
from html import unescape

import json

HERE = os.path.dirname(os.path.abspath(__file__))
DEFINITION = os.path.join(HERE, "..", "..", "src", "main", "resources", "default-project", "definition.json")

ap = argparse.ArgumentParser()
ap.add_argument("--rdf", default=os.path.join(HERE, "siamois-socle-commun.rdf"))
ap.add_argument("--definition", default=DEFINITION)
ap.add_argument("--thesaurus-url", help="remplace l'URL du thésaurus de la définition")
ap.add_argument("--all-types", nargs="*", default=["DOCUMENT"], help="tables dont tous les types du thésaurus sont repris")
args = ap.parse_args()

text = open(args.rdf, encoding="utf8").read()
concepts, collections = {}, {}
for about, body in re.findall(r'<rdf:Description rdf:about="([^"]*)">(.*?)</rdf:Description>', text, re.S):
    fr = unescape((re.findall(r'prefLabel xml:lang="fr">([^<]*)', body) or [""])[0])
    m = re.search(r"idc=(\d+)", about)
    if m and 'Concept"' in body:
        concepts[m.group(1)] = {
            "fr": fr,
            "notation": (re.findall(r"<skos:notation>([^<]*)", body) or [None])[0],
            "narrower": re.findall(r'skos:narrower rdf:resource="[^"]*idc=(\d+)', body),
        }
    m = re.search(r"idg=(g\d+)", about)
    if m and 'Collection"' in body:
        collections[m.group(1)] = fr
by_notation = {c["notation"]: k for k, c in concepts.items() if c["notation"]}


def lists_for(type_code, label):
    found = {}
    for gid, name in collections.items():
        m = re.match(r".* — (.*?)  \[(.*?) @ (.*?):(.*?)\]$", name)
        if m and m.group(3) == type_code and m.group(1) == label:
            found[m.group(2)] = (gid, name)
    return {code: found[code][0] for code in sorted(found)}, {code: found[code][1] for code in sorted(found)}


definition = json.load(open(args.definition, encoding="utf8"))
if args.thesaurus_url:
    definition["thesaurus"] = args.thesaurus_url
for table in definition["tables"]:
    notation = "SIAMOIS#" + table["typeCode"]
    if notation not in by_notation:
        sys.exit("Le thésaurus ne définit pas %s (table %s)" % (table["typeCode"], table["table"]))
    children = {concepts[c]["fr"]: c for c in concepts[by_notation[notation]]["narrower"]}
    if table["table"] in args.all_types:
        table["types"] = [{"label": label, "concept": cid} for label, cid in children.items()]
    for type_ in table["types"]:
        if type_["label"] not in children:
            sys.exit("Type introuvable sous %s : %s" % (table["typeCode"], type_["label"]))
        type_["concept"] = children[type_["label"]]
        type_.pop("lists", None)
        type_.pop("listNames", None)
        lists, names = lists_for(table["typeCode"], type_["label"])
        if lists:
            type_["lists"], type_["listNames"] = lists, names
open(args.definition, "w", encoding="utf8").write(json.dumps(definition, indent=2, ensure_ascii=False))
print("définition écrite : %s (%d tables, %d collections lues)" % (args.definition, len(definition["tables"]), len(collections)))
