#!/usr/bin/env python3
"""
Correspondance valeurs des Excel Kition-Pervolia -> concepts du thésaurus th182 (lecture seule).

Entrées : l'export complet de th182 (GET /openapi/v1/thesaurus/th182) et les deux xlsx.
Sorties : kition_mapping_th182.json (lu par l'import) et kition_mapping_th182.md (à relire).

Règles retenues pour le prototype :
  - Datation : une seule valeur par fiche. Le « ? » est ignoré ; pour une plage ou deux périodes
    (« CAII-CCI », « CC et contemporain »), on garde la première, la plus ancienne.
  - Tout ce que th182 ne couvre pas est listé « à créer ailleurs » (th182 n'est pas modifiable).

Usage : python3 -I kition_build_mapping.py --th182 th182_full.json --us US.xlsx --locus Locus.xlsx --out .
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from kition_to_siamois_api import load_xlsx  # noqa: E402

SK = "http://www.w3.org/2004/02/skos/core#"

# Valeurs dont le libellé diffère de celui de th182 : décision manuelle (valeur source en minuscules -> libellé th182).
NATURE_LOCUS = {
    "niche": "niche",
    "dromos d'une tombe": "Dromos",
    "chambre d'une tombe": "Chambre funéraire",
    "stomion d'une tombe": "stomion",
    "niveau de sol": "Niveau de sol",
    "creusement dans le rocher": "Creusement",
    "dépression probablement naturelle dans le substrat rocheux": "Dépression naturelle",
    "fosse": "Fosse",
    "structure de pierres sèches": "Structure en pierre",
}
# Rôle du document dans l'import -> (libellé th182, concept th338 « Type de document »).
TYPE_DOCUMENT = {
    "carnet": ("Cahier de fouille", "1708", "carnet de fouille"),
    "relevé": ("Relevé", "1719", "relevé de terrain"),
    "photo de terrain": ("Photographie de fouille", "1724", "photographie de terrain"),
    "photo d'objet": ("Photographie d'objet", "1726", "photographie d'objet"),
}
INTERPRETATION = {
    "comblement": "Comblement",
    "comblement du dromos": "Comblement",
    "surface": "surface",
    "surface et comblement": "surface",  # une seule valeur : la première
    "remblai de la fouille 2012": "Remblai",
    "remblai de la fouille 2013": "Remblai",
}


def fold(s: str) -> str:
    s = unicodedata.normalize("NFC", s.lower().replace("’", "'")).strip()
    return re.sub(r"\s+", " ", s)


def load_index(path: Path):
    data = json.loads(path.read_text())
    byid, index = {}, {}
    for uri, node in data.items():
        if SK + "prefLabel" not in node or "idc=" not in uri:
            continue
        cid = uri.split("idc=")[1].split("&")[0]
        fr = [x["value"] for x in node[SK + "prefLabel"] if x.get("lang") == "fr"]
        byid[cid] = fr[0] if fr else node[SK + "prefLabel"][0]["value"]
        for key in ("prefLabel", "altLabel"):
            for x in node.get(SK + key, []):
                index.setdefault(fold(x["value"]), cid)
    return byid, index


def column(rows, col):
    return Counter((r.get(col) or "").strip() for r in rows if (r.get(col) or "").strip())


def datation(raw: str, index):
    """(concept, remarque). Une seule période ; « ? » ignoré ; plage -> première période."""
    if not raw.strip("? "):
        return None, "vide ou « ? »"
    text = raw.replace("?", " ")
    parts = [p.strip(" .") for p in re.split(r"\s*[-/,]\s*|\s+et\s+", text) if p.strip(" .")]
    first = index.get(fold(parts[0]))
    note = None
    if len(parts) > 1:
        note = f"plage ou 2 périodes, première retenue (« {' + '.join(parts)} »)"
    elif "?" in raw:
        note = "« ? » ignoré"
    return first, note


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--th182", required=True)
    ap.add_argument("--us", required=True)
    ap.add_argument("--locus", required=True)
    ap.add_argument("--out", default=".")
    a = ap.parse_args()

    byid, index = load_index(Path(a.th182))
    by_label = {fold(v): k for k, v in byid.items()}
    _, us = load_xlsx(a.us)
    _, lo = load_xlsx(a.locus)

    mapping = {"datation": {}, "natureLocus": {}, "interpretation": {}, "auteurs": {}, "typeDocument": {}}
    missing = defaultdict(list)

    # Datation (US M, Locus A)
    for src, cnt in (column(us, "M") + column(lo, "A")).most_common():
        cid, note = datation(src, index)
        mapping["datation"][src] = {"concept": cid, "label": byid.get(cid), "n": cnt, "note": note}
        if cid is None and note != "vide ou « ? »":
            missing["Datation"].append(f"{src} (×{cnt})")

    # Nature du locus (Locus M)
    for src, cnt in column(lo, "M").most_common():
        label = NATURE_LOCUS.get(fold(src))
        cid = by_label.get(fold(label)) if label else None
        mapping["natureLocus"][src] = {"concept": cid, "label": byid.get(cid), "n": cnt}
        if cid is None:
            missing["Nature du locus"].append(f"{src} (×{cnt})")

    # Interprétation (US T)
    for src, cnt in column(us, "T").most_common():
        label = INTERPRETATION.get(fold(src))
        cid = by_label.get(fold(label)) if label else None
        mapping["interpretation"][src] = {"concept": cid, "label": byid.get(cid), "n": cnt}
        if cid is None:
            missing["Interprétation de l'US"].append(f"{src} (×{cnt})")

    # Auteurs (personnes de la branche Personne)
    for src, cnt in (column(us, "AD") + column(lo, "O")).items():
        for name in re.split(r"[\n,]", src):
            name = {"Annas Cannavò": "Anna Cannavò", "Nathalia Deninger": "Nathalia Denninger"}.get(name.strip(), name.strip())
            cid = by_label.get(fold(name))
            mapping["auteurs"][name] = {"concept": cid, "label": byid.get(cid)}

    # Types de document (rôles posés par l'import)
    for role, (label182, id338, label338) in TYPE_DOCUMENT.items():
        cid = by_label.get(fold(label182))
        mapping["typeDocument"][role] = {"th182": cid, "label182": byid.get(cid), "th338": id338, "label338": label338}

    # Vocabulaires entièrement absents de th182
    for title, col, rows in (("Fiabilité", "Y", us), ("Homogénéité", "Q", us), ("US positive / négative", "O", us), ("Couleur", "R", us)):
        for src, cnt in column(rows, col).most_common():
            missing[title].append(f"{src} (×{cnt})")

    out = Path(a.out)
    (out / "kition_mapping_th182.json").write_text(json.dumps(mapping, ensure_ascii=False, indent=2))

    md = ["# Correspondance Excel Kition → th182", ""]
    for key, title in (("datation", "Datation"), ("natureLocus", "Nature du locus"), ("interpretation", "Interprétation de l'US")):
        md += [f"## {title}", "", "| Valeur source | Fiches | Concept th182 | Remarque |", "|---|---|---|---|"]
        for src, m in mapping[key].items():
            c = f"{m['label']} [{m['concept']}]" if m["concept"] else "**à créer ailleurs**"
            md.append(f"| {src!r} | {m['n']} | {c} | {m.get('note') or ''} |")
        md.append("")
    md += ["## Types de document", "", "| Rôle | th182 | th338 |", "|---|---|---|"]
    for role, m in mapping["typeDocument"].items():
        md.append(f"| {role} | {m['label182']} [{m['th182']}] | {m['label338']} [{m['th338']}] |")
    md.append("")
    md += ["## À créer ailleurs (non couvert par th182)", ""]
    for title, vals in missing.items():
        md += [f"- **{title}** : " + "; ".join(vals)]
    (out / "kition_mapping_th182.md").write_text("\n".join(md) + "\n")

    for key in ("datation", "natureLocus", "interpretation"):
        tot = sum(m["n"] for m in mapping[key].values())
        ok = sum(m["n"] for m in mapping[key].values() if m["concept"])
        print(f"{key}: {ok}/{tot} fiches avec concept")


if __name__ == "__main__":
    main()
