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
Pour les tables de --all-types (toutes par défaut), les types sont tous ceux que le thésaurus déclare ; le format d'identifiant
des types est recalculé d'après PREFIXES ; sinon chaque type reçoit un préfixe
parlant et unique dans sa table (voir PREFIXES).
Il s'arrête si un type de la définition n'existe plus dans le thésaurus.
"""
import argparse
import os
import re
import sys
import unicodedata
from html import unescape

import json

HERE = os.path.dirname(os.path.abspath(__file__))
DEFINITION = os.path.join(HERE, "..", "..", "src", "main", "resources", "default-project", "definition.json")

ap = argparse.ArgumentParser()
ap.add_argument("--rdf", default=os.path.join(HERE, "siamois-socle-commun.rdf"))
ap.add_argument("--definition", default=DEFINITION)
ap.add_argument("--thesaurus-url", help="remplace l'URL du thésaurus de la définition")
ap.add_argument("--all-types", nargs="*", default=["UE", "MOBILIER", "PHASE", "CONTENANT", "DOCUMENT", "STUDY"],
                help="tables dont tous les types du thésaurus sont repris (par défaut toutes)")
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


# La partie compteur du format d'identifiant par défaut de chaque table (ConfigurableTable).
COUNTERS = {"UE": "{NUM_UE:000}", "MOBILIER": "{NUM_MOBILIER:000}", "PHASE": "{NUM_PHASE:000}",
            "CONTENANT": "{NUM_CONTAINER:000}", "DOCUMENT": "{NUM_DOCUMENT:0000}", "STUDY": "{NUM_ETUDE:0000}"}
# Le préfixe d'identifiant de chaque type, par table : court, parlant, unique dans sa table.
PREFIXES = {
    "UE": {"US": "US", "Fait": "F", "Ensemble": "ENS", "UF": "UF", "Sépulture": "SEP", "Sondage": "SD", "Tranchée": "TR",
           "Zone": "Z", "Secteur": "SECT", "Carré": "CAR", "Log": "LOG", "USlog": "USLOG", "Unité artificielle": "UA",
           "Passe mécanique": "PM", "Nettoyage": "NET", "Fenêtre de fouille": "FEN",
           "Entité archéologique": "EA", "ensemble archéologique": "ENSA", "Entité spatiale": "ESP", "Entité graphique": "EG",
           "Unité graphique": "UG", "Entité taphonomique": "ET", "Phase": "PH"},
    "MOBILIER": {"Céramique": "CER", "Métal": "MET", "Industrie lithique": "LIT", "Verre": "VER", "Faune": "FAU",
                 "Terre cuite architecturale": "TCA", "Vestige anthropobiologique (VAB)": "VAB", "Prélèvement": "PRE"},
    "PHASE": {"Phase d'occupation": "OCC", "Phase de construction": "CON", "Phase d'aménagement ou de transformation": "AME",
              "Phase d'abandon ou de destruction": "ABA"},
    "CONTENANT": {"palette": "PAL", "caisse norme Europe": "CAI", "boîte hermétique transparente": "BHT", "chemise": "CHE",
                  "boîte à archives": "BAR", "carton à dessins": "CDE", "cassette audio": "CAU", "cassette vidéo": "CVI",
                  "DVD": "DVD", "carte SD": "SD", "adresse du serveur": "SRV", "sachet": "SAC", "Disque dur": "HDD",
                  "Cédérom": "CD", "Support amovible": "SUP"},
    "DOCUMENT": {"carnet de fouille": "CAR", "rapport étude": "RAP", "fiche d'UE": "FUE", "fiche d'objet": "FOB",
                 "fiche de conservation par individu": "FCI", "diagramme stratigraphique": "DST",
                 "liste de points topographiques": "LPT", "base de données": "BDD", "carte": "CTE", "vue aérienne": "VAE",
                 "plan topographique": "PTO", "relevé de terrain": "RTE", "relevé d'art rupestre": "RAR",
                 "dessin d'objet": "DOB", "relevé des traces d'activités humaines et animales": "RTA", "radiographie": "RAD",
                 "photographie de terrain": "PHT", "photographie redressée": "PHR", "photographie d'objet": "PHO",
                 "orthophotographie": "ORT", "enregistrement audio": "EAU", "enregistrement vidéo": "EVI", "empreinte": "EMP",
                 "tirage": "TIR", "arraché de coupe": "ARR", "moule": "MOU", "nuage de points": "NPT", "modèle 3D": "M3D",
                 "prototypage": "PRO", "Journal de fouille": "JDF", "Fiche de prélèvement": "FPR", "Fiche de structure": "FST",
                 "Registre de photographies": "RPH", "Minute de fouille": "MIN", "Relevé de coupe": "RCO",
                 "Relevé en plan": "RPL", "Relevé d'élévation": "REL", "Croquis de terrain": "CRO",
                 "Log stratigraphique": "LST", "Couche SIG": "SIG"},
    "STUDY": {"stabilisation": "STA", "consolidation": "CSL", "analyse ADN": "ADN", "étude de pâte ou de pigment": "EPP",
              "marquage": "MAR", "radiographie": "RAD", "mise en état pour étude": "MEE", "remontage": "REM",
              "Nettoyage (conservation)": "NET", "Dessalement": "DES", "Lyophilisation": "LYO", "Imprégnation": "IMP",
              "Dégagement": "DEG", "Collage": "COL", "Comblement de lacune": "CLA", "Constat d'état": "CET", "Lavage": "LAV",
              "Tamisage à l'eau": "TAM", "Flottation": "FLO", "Tri": "TRI", "Méthode du carbone 14": "C14",
              "Analyse isotopique": "ISO", "Datation dendrochronologique": "DEN", "Analyse métallographique": "MET",
              "Lame mince": "LMI"},
}


def assign_prefixes(table):
    """Chaque type d'une table a un format d'identifiant `<préfixe>{compteur}`, le préfixe étant unique dans la table.

    Le préfixe vient de PREFIXES ; un type qui n'y figure pas garde son format actuel, ou à défaut reçoit les trois
    premières lettres de son libellé. Le script s'arrête si deux types d'une table partagent un préfixe."""
    counter = COUNTERS[table["table"]]
    named = PREFIXES.get(table["table"], {})
    seen = {}
    for type_ in table["types"]:
        label = type_["label"]
        if label in named:
            prefix = named[label]
        elif type_.get("identifierFormat"):
            prefix = type_["identifierFormat"].split("{")[0]
        else:
            ascii_label = unicodedata.normalize("NFKD", label).encode("ascii", "ignore").decode()
            prefix = re.sub(r"[^A-Za-z0-9]", "", ascii_label)[:3].upper()
        if prefix in seen:
            sys.exit("Préfixe %r en double dans %s : %s et %s" % (prefix, table["table"], seen[prefix], label))
        seen[prefix] = label
        type_["identifierFormat"] = prefix + counter


definition = json.load(open(args.definition, encoding="utf8"))
if args.thesaurus_url:
    definition["thesaurus"] = args.thesaurus_url
for table in definition["tables"]:
    notation = "SIAMOIS#" + table["typeCode"]
    if notation not in by_notation:
        sys.exit("Le thésaurus ne définit pas %s (table %s)" % (table["typeCode"], table["table"]))
    children = {concepts[c]["fr"]: c for c in concepts[by_notation[notation]]["narrower"]}
    if table["table"] in args.all_types:
        known = {t["label"]: t for t in table["types"]}
        table["types"] = [{"label": label, "concept": cid,
                           **({"identifierFormat": known[label]["identifierFormat"]}
                              if label in known and known[label].get("identifierFormat") else {})}
                          for label, cid in children.items()]
        assign_prefixes(table)
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
