#!/usr/bin/env python3
"""
Enrichit les documents importés (scripts/kition_to_siamois_api.py) avec les métadonnées publiques de
Kition-Pervolia (apichypre.mom.fr, lecture seule) : titre, description, mots-clés, auteur,
lien Nakala (champ « URL externe ») et identifiant ARK (champ « Autre(s) identifiant(s) »).

Usage : python3 kition_enrich_documents.py --project 6
"""
from __future__ import annotations

import argparse
import os
import json
import sys
import urllib.request

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from kition_to_siamois_api import Api  # noqa: E402

SRC = "https://apichypre.mom.fr/KP"
ENTITIES = ["photoTerrain", "photoFouille", "photoMobilier", "dessinMobilier"]
F_TITLE, F_DESC, F_OTHER_IDS, F_URL = "-708", "-713", "-702", "-722"


def post(path, body):
    req = urllib.request.Request(SRC + path, data=json.dumps(body).encode(), method="POST",
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read())


def get(path):
    with urllib.request.urlopen(SRC + path, timeout=60) as r:
        return json.loads(r.read())


def collect():
    meta = {}
    for ent in ENTITIES:
        page = 1
        while True:
            r = post(f"/{ent}/parPage", {"criteres": "Kition", "page": page, "nbElementsParPage": 200})
            for row in r["rows"]:
                meta.setdefault(row["identifiant"], row)
            if page * 200 >= r["count"]:
                break
            page += 1
        print(f"  {ent}: {r['count']}")
    # dessins de terrain : pas de liste paginée, on passe par les loci
    for n in range(1, 80):
        try:
            for row in post("/dessinTerrain/", {"critere": str(n)}):
                meta.setdefault(row["identifiant"], row)
        except Exception:
            pass
    for code in ["KIT12-NIMRD-0001", "KIT12-NIMRD-0002", "KIT12-NIMRD-0003", "KIT12-NIMRD-0004",
                 "KIT12-NIMRD-0005", "KIT12-NANEA-0001", "KIT13-NIMRD-0003", "KIT14-NIMRD-0002"]:
        try:
            row = get(f"/carnet/{code}")
            if row:
                meta.setdefault(code, row)
        except Exception:
            pass
    return meta


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--project", required=True)
    ap.add_argument("--base", default=os.environ.get("SIAMOIS_URL", "http://localhost:8080/siamois/api/v1"))
    ap.add_argument("--email", default=os.environ.get("SIAMOIS_EMAIL"))
    ap.add_argument("--password", default=os.environ.get("SIAMOIS_PASSWORD"))
    args = ap.parse_args()

    meta = collect()
    print(f"métadonnées source : {len(meta)}")

    api = Api(args.base, args.email, args.password)
    docs, offset = [], 0
    while True:
        st, r = api.call("GET", f"/projects/{args.project}/documents?limit=200&offset={offset}")
        docs += [(d["id"], d.get("title") or d.get("identifier")) for d in r["data"]]
        if offset + 200 >= r["meta"]["total"]:
            break
        offset += 200
    print(f"documents Siamois : {len(docs)}")

    done = missing = errors = 0
    for doc_id, code in docs:
        m = meta.get(code)
        if not m:
            missing += 1
            continue
        desc = m.get("description") or ""
        lines = [desc]
        if m.get("mots_cle"):
            lines.append(f"Mots-clés : {m['mots_cle']}")
        if m.get("auteur"):
            lines.append(f"Auteur : {m['auteur']}")
        answers = {F_DESC: {"value": "\n".join(l for l in lines if l)[:5000]}}
        if desc:
            answers[F_TITLE] = {"value": desc[:255]}
        if m.get("url_perenne"):
            answers[F_URL] = {"value": m["url_perenne"]}
        if m.get("url_ark"):
            answers[F_OTHER_IDS] = {"value": m["url_ark"]}
        st, r = api.call("PATCH", f"/documents/{doc_id}", {"answers": answers})
        if st == 200:
            done += 1
        else:
            errors += 1
            if errors <= 5:
                print("  erreur", code, st, json.dumps(r, ensure_ascii=False)[:200])
    print(f"enrichis : {done}, sans métadonnées : {missing}, erreurs : {errors}")


if __name__ == "__main__":
    main()
