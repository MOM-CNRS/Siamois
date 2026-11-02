#!/usr/bin/env python3
"""
Migration unique des exports Kition-Pervolia (feuilles US et Locus) vers Siamois, par l'API REST.

Modèle appliqué :
  - Sondage (S2..S5)         -> UE de type « Sondage », parent des loci et des US du sondage
  - Tombe (colonne US)       -> UE de type « Sépulture », parent des US de la tombe
  - Locus                    -> UE de type « Fait », identifiant « Locus N »
  - US                       -> UE de type « US », identifiant d'origine (S4-03)
  - « sous / sur / recoupe / va avec / = »  -> relations stratigraphiques
  - « dans / contient »      -> hiérarchie des UE (parents)
  - codes photo / relevé / cahier (KIT13-NIMFO-0010…) -> documents sans fichier, liés aux UE
Le projet est créé avec le gabarit « kition » (types et listes lus dans th182) ; les valeurs de datation, de nature du
locus et les types de document sont retrouvés dans th182 grâce à kition_mapping_th182.json (voir kition_build_mapping.py).
Homogénéité, fiabilité et interprétation libre vont dans les champs texte du gabarit. Ce qui n'a pas de champ cible
(cotes Z, matériel, auteur, localisation précise…) est regroupé dans la description de l'UE.

Usage :
    python3 kition_to_siamois_api.py --us US.xlsx --locus Locus.xlsx --project 5 --dry-run
    python3 kition_to_siamois_api.py --us US.xlsx --locus Locus.xlsx --project 5
    python3 kition_to_siamois_api.py --us US.xlsx --locus Locus.xlsx --create-project "Kition-Pervolia" --organization 1 \
        --base https://serveur/siamois/api/v1 --email ... --password ...

Dépendances : bibliothèque standard uniquement.
"""
from __future__ import annotations

import argparse
import os
import json
import re
import sys
import time
import urllib.error
import urllib.request
import zipfile
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from datetime import date, datetime, timedelta
from pathlib import Path

NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}

# --- Types : clés logiques, résolues sur le projet créé par le gabarit « kition » (identifiant th182 du concept) ---
TYPE_SONDAGE, TYPE_SEPULTURE, TYPE_FAIT, TYPE_US = "sondage", "tombe", "locus", "us"
UE_TYPE_TH182 = {TYPE_SONDAGE: "42434", TYPE_SEPULTURE: "42435", TYPE_FAIT: "42433", TYPE_US: "42436"}
DOC_ROLE_CAHIER, DOC_ROLE_RELEVE, DOC_ROLE_PHOTO_TERRAIN, DOC_ROLE_PHOTO_OBJET = "carnet", "relevé", "photo de terrain", "photo d'objet"
# Champs système de l'UE
F_DATATION, F_INTERPRETATION, F_NATURE = "-314", "-308", "-331"
F_AUTHOR, F_CONTRIBUTORS = "-301", "-309"
# Champs texte ajoutés par le gabarit (leur identifiant dépend du type : lus sur le projet)
TEXT_INTERP, TEXT_HOMOG, TEXT_FIAB = "Interprétation (texte libre)", "Homogénéité", "Fiabilité"
TEXT_DATING, TEXT_MATERIAL, TEXT_INVENTORY = "Commentaire de datation", "Types de matériel", "Inventaire du matériel"
TEXT_NOTEBOOK, TEXT_AUTHORS = "Renvois au cahier de fouille", "Auteurs de la fiche"
DATE_CREATED, DATE_MODIFIED = "Date de création de la fiche", "Date de modification de la fiche"
Z_FIELDS = ("Z inférieur 1", "Z inférieur 2", "Z supérieur 1", "Z supérieur 2")
STRATI = {  # verbe -> (position de l'autre UE, code de la liste, libellé du concept lu de l'UE vers l'autre)
    "sous": ("posterior", "SIARU.STRATI.POSTERIOR", "est recouverte par"),
    "sur": ("anterior", "SIARU.STRATI.ANTERIOR", "recouvre"),
    "recoupe": ("anterior", "SIARU.STRATI.ANTERIOR", "coupe"),
    "va avec": ("synchronous", "SIARU.STRATI.SYNCHRONOUS", "contemporain de"),
    "=": ("synchronous", "SIARU.STRATI.SYNCHRONOUS", "équivalent à"),
}

AUTHOR_FIX = {"Annas Cannavò": "Anna Cannavò", "Nathalia Deninger": "Nathalia Denninger"}


# --- Lecture xlsx (sans dépendance) ---------------------------------------------------------------
def load_xlsx(path: str):
    z = zipfile.ZipFile(path)
    ss = [
        "".join(t.text or "" for t in si.iter("{%s}t" % NS["m"]))
        for si in ET.fromstring(z.read("xl/sharedStrings.xml")).findall("m:si", NS)
    ]
    rows = ET.fromstring(z.read("xl/worksheets/sheet1.xml")).findall(".//m:row", NS)

    def val(c):
        v = c.find("m:v", NS)
        if c.get("t") == "s" and v is not None:
            return ss[int(v.text)]
        return v.text if v is not None else None

    data = [{re.match(r"[A-Z]+", c.get("r")).group(): val(c) for c in r.findall("m:c", NS)} for r in rows]
    return data[0], data[1:]


def clean(s):
    return None if s is None else (re.sub(r"[ \t]+", " ", s).strip() or None)


def fr_date(s):
    m = re.fullmatch(r"(\d{1,2})/(\d{1,2})/(\d{4})", (s or "").strip())
    return f"{m.group(3)}-{int(m.group(2)):02d}-{int(m.group(1)):02d}" if m else None


def serial_date(s):
    try:
        return (date(1899, 12, 30) + timedelta(days=int(float(s)))).isoformat()
    except (TypeError, ValueError):
        return None


def num(s):
    try:
        return round(float(str(s).replace(",", ".")), 3)
    except (TypeError, ValueError):
        return None


def fold_name(name):
    return re.sub(r"\s+", " ", name.strip().lower())


def sondages(cell):
    """Les sondages d'une fiche : « S4 et S5 » est une UE dans deux sondages (hiérarchie multiple)."""
    return [x for x in (clean(p) for p in re.split(r"\s+et\s+|\s*[,;/]\s*", cell or "")) if x]


def authors(s):
    out = []
    for a in re.split(r"[\n,]", s or ""):
        a = AUTHOR_FIX.get(a.strip(), a.strip())
        if a and a not in out:
            out.append(a)
    return out


# --- Modèle intermédiaire --------------------------------------------------------------------------
class UE:
    def __init__(self, ident, type_id):
        self.ident, self.type_id = ident, type_id
        self.answers = {}
        self.comments = []
        self.parent_idents = []  # parents hiérarchiques (le premier est posé à la création)
        self.rel_text = []       # textes de relation (US + locus)
        self.docs = []           # (code, rôle)
        self.pending = {}        # valeur d'origine d'un champ à vocabulaire : datation, nature du locus, interprétation, nature
        self.texts = {}          # champs texte du gabarit : libellé -> valeur
        self.dates = {}          # champs date du gabarit : libellé -> date ISO
        self.measures = {}       # champs mesure du gabarit (mètres) : libellé -> valeur
        self.author_names = []   # auteurs de la fiche d'origine, à retrouver parmi les personnes du serveur
        self.id = None


def doc_codes(cell):
    return re.findall(r"K[A-Z]*\d+-NIM[A-Z]{2}-\d+", cell or "")


def add_docs(ue, cell, role, extra=None):
    for code in doc_codes(cell):
        ue.docs.append((code, role))


def text(ue, label, value):
    value = clean(value) if isinstance(value, str) else value
    if value:
        ue.texts[label] = value


def sheet_fields(ue, authors_cell, created, modified):
    """Auteurs et dates de la fiche d'origine, dans les champs du gabarit."""
    names = authors(authors_cell)
    if names:
        ue.texts[TEXT_AUTHORS] = ", ".join(names)
        ue.author_names = names
    for label, serial in ((DATE_CREATED, created), (DATE_MODIFIED, modified)):
        if serial_date(serial):
            ue.dates[label] = serial_date(serial)


def note(ue, label, value):
    value = clean(value) if isinstance(value, str) else value
    if value:
        ue.comments.append(f"{label} : {value}")


def build(us_path, locus_path):
    ues = {}
    _, us_rows = load_xlsx(us_path)
    _, lo_rows = load_xlsx(locus_path)

    def get(ident, type_id):
        if ident not in ues:
            ues[ident] = UE(ident, type_id)
        return ues[ident]

    # ----- Loci
    for r in lo_rows:
        n = clean(r.get("P"))
        if not n:
            continue
        ue = get(f"Locus {n}", TYPE_FAIT)
        nature = clean(r.get("M"))
        desc = [clean(r.get("E")), f"Nature : {nature}" if nature else None, clean(r.get("N"))]
        ue.answers["-317"] = {"value": " — ".join(d for d in desc if d)}
        if fr_date(r.get("C")):
            ue.answers["-303"] = {"value": fr_date(r["C"])}
        if nature:
            ue.pending["natureLocus"] = nature
            ue.texts[TEXT_INTERP] = nature
        if clean(r.get("A")):
            ue.pending["datation"] = clean(r.get("A"))
        for label, col in zip(Z_FIELDS, "TUVW"):
            if num(r.get(col)) is not None:
                ue.measures[label] = num(r.get(col))
        note(ue, "Datation (source)", r.get("A"))
        note(ue, "Observations", r.get("Q"))
        note(ue, "Localisation précise", r.get("I"))
        text(ue, TEXT_MATERIAL, r.get("S"))
        text(ue, TEXT_INVENTORY, r.get("L"))
        text(ue, TEXT_NOTEBOOK, r.get("H"))
        sheet_fields(ue, r.get("O"), r.get("B"), r.get("D"))
        for sond in sondages(r.get("K")):
            ue.parent_idents.append(sond)
            get(sond, TYPE_SONDAGE)
        ue.rel_text.append(r.get("R") or "")
        add_docs(ue, r.get("F"), None)
        add_docs(ue, r.get("G"), DOC_ROLE_RELEVE)
        add_docs(ue, r.get("H"), DOC_ROLE_CAHIER)

    # ----- US
    for r in us_rows:
        ident = clean(r.get("A"))
        if not ident:
            continue
        ue = get(ident, TYPE_US)
        sed = [clean(r.get("P")), clean(r.get("S"))]
        parts = [f"Sédiment : {sed[0]}" if sed[0] else None,
                 f"Composition : {sed[1]}" if sed[1] else None]
        ue.answers["-317"] = {"value": ". ".join(p for p in parts if p) or None}
        if clean(r.get("R")):
            ue.answers["-325"] = {"value": clean(r["R"])}
        if fr_date(r.get("C")):
            ue.answers["-303"] = {"value": fr_date(r["C"])}
        if fr_date(r.get("D")):
            ue.answers["-318"] = {"value": fr_date(r["D"])}
        o = (r.get("O") or "").strip().lower()
        if o in ("positive", "négative", "negative"):
            ue.pending["nature"] = "Positive" if o == "positive" else "Négative"
        if clean(r.get("M")):
            ue.pending["datation"] = clean(r.get("M"))
        if clean(r.get("T")):
            ue.pending["interpretation"] = clean(r.get("T"))
            ue.texts[TEXT_INTERP] = clean(r.get("T"))
        if clean(r.get("Q")):
            ue.texts[TEXT_HOMOG] = clean(r.get("Q"))
        if clean(r.get("Y")):
            ue.texts[TEXT_FIAB] = clean(r.get("Y"))
        # la fiche US donne sup 1, sup 2, inf 1, inf 2 (colonnes I à L)
        for label, col in zip(("Z supérieur 1", "Z supérieur 2", "Z inférieur 1", "Z inférieur 2"), "IJKL"):
            if num(r.get(col)) is not None:
                ue.measures[label] = num(r.get(col))
        note(ue, "Datation (source)", r.get("M"))
        text(ue, TEXT_DATING, r.get("N"))
        note(ue, "Commentaires", r.get("Z"))
        note(ue, "Emprise", r.get("H"))
        text(ue, TEXT_MATERIAL, r.get("X"))
        text(ue, TEXT_INVENTORY, r.get("W"))
        text(ue, TEXT_NOTEBOOK, r.get("AC"))
        sheet_fields(ue, r.get("AD"), r.get("AE"), r.get("AF"))
        for sond in sondages(r.get("F")):
            ue.parent_idents.append(sond)
            get(sond, TYPE_SONDAGE)
        tombe = clean(r.get("B"))
        if tombe:
            t = get(f"Tombe {tombe}", TYPE_SEPULTURE)
            ue.parent_idents.append(t.ident)
        ue.rel_text += [r.get("U") or "", r.get("V") or ""]
        add_docs(ue, r.get("AA"), None)
        add_docs(ue, r.get("AB"), DOC_ROLE_RELEVE)
        add_docs(ue, r.get("AC"), DOC_ROLE_CAHIER)

    return ues


# --- Relations -------------------------------------------------------------------------------------
REL = re.compile(r"(?P<verb>sous|sur|recoupe|va avec|dans|contient|=|égal)\s*(?P<kind>US|locus)?\s*(?P<id>S\d+-\d+|\d+)", re.I)


def resolve(kind, ident, ues):
    key = f"Locus {ident}" if (kind or "").lower() == "locus" else ident
    return key if key in ues else None


def relations(ues):
    strati, hier, unresolved = {}, set(), []
    for ue in ues.values():
        for text in ue.rel_text:
            for part in re.split(r"[;\n]", text):
                for m in REL.finditer(part):
                    verb = m.group("verb").lower()
                    verb = "=" if verb == "égal" else verb
                    other = resolve(m.group("kind"), m.group("id"), ues)
                    if other is None:
                        unresolved.append((ue.ident, m.group(0)))
                        continue
                    if other == ue.ident:
                        continue
                    if verb == "dans":
                        hier.add((other, ue.ident))
                    elif verb == "contient":
                        hier.add((ue.ident, other))
                    else:
                        key = frozenset((ue.ident, other))
                        if key not in strati:
                            strati[key] = (ue.ident, other, verb)
    return list(strati.values()), sorted(hier), unresolved


# --- Client API ------------------------------------------------------------------------------------
class Api:
    def __init__(self, base, email, password):
        self.base, self.email, self.password = base.rstrip("/"), email, password
        self.token = None
        self.calls = Counter()
        self.login()

    def login(self):
        status, r = self.raw("POST", "/auth/login", {"email": self.email, "password": self.password}, auth=False)
        if status != 200 or not isinstance(r, dict) or "accessToken" not in r:
            detail = (r.get("message") or r.get("error") or r) if isinstance(r, dict) else r
            raise SystemExit(f"Connexion refusée par {self.base} pour {self.email} (HTTP {status}) : {detail}")
        self.token = r["accessToken"]
        self.logged_at = time.time()

    def raw(self, method, path, body=None, auth=True):
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(self.base + path, data=data, method=method)
        req.add_header("Content-Type", "application/json")
        if auth:
            req.add_header("Authorization", f"Bearer {self.token}")
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                txt = resp.read().decode()
                return resp.status, (json.loads(txt) if txt else None)
        except urllib.error.HTTPError as e:
            txt = e.read().decode()
            try:
                return e.code, json.loads(txt)
            except ValueError:
                return e.code, {"error": txt[:200]}

    def call(self, method, path, body=None):
        if time.time() - self.logged_at > 600:
            self.login()
        self.calls[method] += 1
        return self.raw(method, path, body)


def resolve_organization(api, value):
    """L'identifiant d'une organisation donnée par son identifiant ou par son nom (« Chypre »)."""
    if str(value).isdigit():
        return str(value)
    st, r = api.call("GET", "/organizations?limit=100")
    if st != 200:
        raise SystemExit(f"Organisations illisibles ({st}) : {r}")
    found = [o for o in r["data"] if o["name"].strip().lower() == str(value).strip().lower()]
    if not found:
        raise SystemExit(f"Organisation « {value} » introuvable parmi : " + ", ".join(o["name"] for o in r["data"]))
    return str(found[0]["id"])


def resolve_project_type(api, org, value):
    """L'identifiant du concept « type de projet » donné par son identifiant ou par son libellé (« Fouille programmée »)."""
    if str(value).isdigit():
        return str(value)
    st, r = api.call("GET", f"/organizations/{org}/concepts?fieldCode=SIAAU.TYPE&limit=100")
    if st != 200:
        raise SystemExit(f"Types de projet illisibles ({st}) : {r}")
    found = [c for c in r["data"] if c["resolvedLabel"].strip().lower() == str(value).strip().lower()]
    if not found:
        raise SystemExit(f"Type de projet « {value} » introuvable parmi : " + ", ".join(c["resolvedLabel"] for c in r["data"]))
    return str(found[0]["id"])


def concept_map(api, field_id, project_id, type_id):
    """Concepts qu'un champ offre pour un type : identifiant th182 du concept -> identifiant interne."""
    out, offset = {}, 0
    while True:
        st, r = api.call("GET", f"/organizations/{api.org}/concepts?fieldId={field_id}&projectId={project_id}"
                                f"&valueConceptId={type_id}&limit=200&offset={offset}")
        if st != 200:
            raise SystemExit(f"Concepts du champ {field_id} introuvables ({st}) : {r}")
        for c in r["data"]:
            out[str(c["externalUrl"])] = str(c["id"])
        offset += 200
        if offset >= r.get("meta", {}).get("total", len(r["data"])):
            return out


def load_project(api, project_id):
    """Types d'UE et de document du projet, champs de leur formulaire, concepts de leurs listes."""
    ctx = {"ue": {}, "doc": {}, "fields": {}, "concepts": {}}
    for kind, path in (("ue", "recording-unit-types"), ("doc", "document-types")):
        st, r = api.call("GET", f"/projects/{project_id}/{path}?limit=100")
        if st != 200:
            raise SystemExit(f"Types du projet {project_id} illisibles ({st}) : {r}")
        for t in r["data"]:
            ctx[kind][str(t["concept"]["externalUrl"])] = str(t["id"])
            if kind == "ue":
                ctx["fields"][str(t["id"])] = {f["label"]: fid for fid, f in t["fields"].items()}
    missing = [k for k in UE_TYPE_TH182.values() if k not in ctx["ue"]]
    if missing:
        raise SystemExit(f"Le projet {project_id} n'a pas les types d'UE {missing} : créé sans le gabarit « kition » ?")
    ctx["concepts"][("chrono", None)] = concept_map(api, F_DATATION, project_id, ctx["ue"][UE_TYPE_TH182[TYPE_US]])
    ctx["concepts"][("natureLocus", None)] = concept_map(api, F_INTERPRETATION, project_id, ctx["ue"][UE_TYPE_TH182[TYPE_FAIT]])
    ctx["concepts"][("interpretation", None)] = concept_map(api, F_INTERPRETATION, project_id, ctx["ue"][UE_TYPE_TH182[TYPE_US]])
    ctx["strati"] = {}
    for verb, (position, code, label) in STRATI.items():
        st, r = api.call("GET", f"/organizations/{api.org}/concepts?fieldCode={code}&limit=100")
        found = [str(c["id"]) for c in (r["data"] if st == 200 else []) if c["resolvedLabel"] == label]
        if not found:
            raise SystemExit(f"Relation « {label} » introuvable dans {code} ({st})")
        ctx["strati"][verb] = found[0]
    return ctx


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--us", required=True)
    ap.add_argument("--locus", required=True)
    ap.add_argument("--project", help="projet existant, créé avec le gabarit « kition »")
    ap.add_argument("--create-project", metavar="NOM", help="crée le projet avec le gabarit « kition »")
    ap.add_argument("--organization", help="organisation du projet à créer (identifiant ou nom)")
    ap.add_argument("--project-type", help="type de projet (identifiant du concept ou libellé, ex. « Fouille programmée »)")
    ap.add_argument("--mapping", default=str(Path(__file__).with_name("kition_mapping_th182.json")))
    ap.add_argument("--base", default=os.environ.get("SIAMOIS_URL", "http://localhost:8080/siamois/api/v1"))
    ap.add_argument("--email", default=os.environ.get("SIAMOIS_EMAIL"))
    ap.add_argument("--password", default=os.environ.get("SIAMOIS_PASSWORD"))
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--report", default="rapport_import_kition.md")
    args = ap.parse_args()
    if not args.dry_run and bool(args.project) == bool(args.create_project):
        ap.error("donner --project ou --create-project (avec --organization et --project-type)")

    mapping = json.loads(Path(args.mapping).read_text(encoding="utf-8"))
    ues = build(args.us, args.locus)
    strati, hier, unresolved = relations(ues)
    docs = {}
    for ue in ues.values():
        for code, role in ue.docs:
            d = docs.setdefault(code, {"role": None, "ues": []})
            if "NIMRD" in code:
                d["role"] = DOC_ROLE_CAHIER
            elif role == DOC_ROLE_RELEVE:
                d["role"] = DOC_ROLE_RELEVE
            elif d["role"] is None:
                d["role"] = DOC_ROLE_PHOTO_OBJET if "NIMMO" in code else DOC_ROLE_PHOTO_TERRAIN
            if ue.ident not in d["ues"]:
                d["ues"].append(ue.ident)

    report = []
    report.append(f"UE : {len(ues)} ({Counter(u.type_id for u in ues.values())})")
    report.append(f"Relations stratigraphiques : {len(strati)} ({Counter(v for _, _, v in strati)})")
    report.append(f"Liens hiérarchiques (dans/contient) : {len(hier)}")
    report.append(f"Documents : {len(docs)} ({Counter(d['role'] for d in docs.values())})")
    report.append(f"Références de relation non résolues : {len(unresolved)}")
    for a, b in unresolved[:40]:
        report.append(f"  - {a} : « {b} »")
    print("\n".join(report))
    if args.dry_run:
        Path(args.report).write_text("\n".join(report), encoding="utf-8")
        return

    api = Api(args.base, args.email, args.password)
    api.org = resolve_organization(api, args.organization or "1")
    if args.project_type:
        args.project_type = resolve_project_type(api, api.org, args.project_type)
    errors, truncated, unmatched = [], [], defaultdict(Counter)

    def fail(what, status, body):
        errors.append(f"{what} -> {status} {json.dumps(body, ensure_ascii=False)[:200]}")

    # Auteurs : les personnes (comptes) de l'organisation, retrouvées par « prénom nom »
    people, offset = {}, 0
    while True:
        st, r = api.call("GET", f"/users?organizationId={api.org}&limit=100&offset={offset}")
        if st != 200:
            raise SystemExit(f"Personnes de l'organisation illisibles ({st}) : {r}")
        for u in r["data"]:
            people[fold_name(f"{u['name']} {u['lastname']}")] = str(u["id"])
        offset += 100
        if offset >= r["meta"]["total"]:
            break
    wanted = {n for ue in ues.values() for n in ue.author_names}
    absent = sorted(n for n in wanted if fold_name(n) not in people)
    if absent:
        raise SystemExit("Personnes introuvables dans l'organisation (à créer et à rattacher à l'organisation ou au projet) : "
                         + ", ".join(absent))

    # 0. Projet : créé avec le gabarit « kition » (types, listes th182, champs texte)
    project = args.project
    if args.create_project:
        st, r = api.call("POST", "/projects", {"organizationId": api.org, "name": args.create_project,
                                              "templateId": "kition", "typeConceptId": args.project_type})
        if st != 201:
            raise SystemExit(f"Création du projet impossible ({st}) : {r}")
        project = r["data"]["id"]
        print(f"Projet créé : {project}", flush=True)
    ctx = load_project(api, project)
    report.append(f"Projet : {project}")



    def resolve_pending(ue):
        """Réponses des champs à vocabulaire, d'après la correspondance th182 ; ce qui n'y est pas est compté."""
        out = {}
        for key, field in (("datation", F_DATATION), ("natureLocus", F_INTERPRETATION), ("interpretation", F_INTERPRETATION)):
            raw = ue.pending.get(key)
            if raw is None:
                continue
            entry = mapping["datation" if key == "datation" else key].get(raw)
            ext = entry and entry.get("concept")
            pool = ctx["concepts"][("chrono" if key == "datation" else key, None)]
            if ext and ext in pool:
                out[field] = {"value": pool[ext]}
            else:
                unmatched[key][raw] += 1
        return out

    # 1. UE : sondages et tombes d'abord (parents), puis loci, puis US
    nature_ids = {}
    st, r = api.call("GET", f"/organizations/{api.org}/concepts?fieldId={F_NATURE}&projectId={project}"
                            f"&valueConceptId={ctx['ue'][UE_TYPE_TH182[TYPE_US]]}&limit=200")
    for c in r["data"]:
        nature_ids[c["resolvedLabel"]] = str(c["id"])

    order = sorted(ues.values(), key=lambda u: {TYPE_SONDAGE: 0, TYPE_SEPULTURE: 1, TYPE_FAIT: 2, TYPE_US: 3}[u.type_id])
    for i, ue in enumerate(order, 1):
        type_id = ctx["ue"][UE_TYPE_TH182[ue.type_id]]
        answers = {k: v for k, v in ue.answers.items() if v.get("value") is not None}
        answers.update(resolve_pending(ue))
        if ue.pending.get("nature") in nature_ids:
            answers[F_NATURE] = {"value": nature_ids[ue.pending["nature"]]}
        if ue.author_names:
            answers[F_AUTHOR] = {"value": people[fold_name(ue.author_names[0])]}
            # tous les auteurs de la fiche contribuent : sans cela le champ prend le compte qui importe
            answers[F_CONTRIBUTORS] = {"value": [people[fold_name(n)] for n in ue.author_names]}
        fields = ctx["fields"][type_id]
        for label, value in ue.texts.items():
            if fields.get(label):
                answers[fields[label]] = {"value": value[:20000]}
        for label, value in ue.dates.items():
            if fields.get(label):
                answers[fields[label]] = {"value": value}
        for label, value in ue.measures.items():
            if fields.get(label):
                answers[fields[label]] = {"value": value}
        # Le champ Commentaires est limité à 255 caractères : les notes vont dans la description (5000).
        text = "\n\n".join(p for p in (answers.get("-317", {}).get("value"), "\n".join(ue.comments)) if p)
        if len(text) > 5000:
            truncated.append(ue.ident)
            text = text[:4990] + " […]"
        if text:
            answers["-317"] = {"value": text}
        body = {"projectId": project, "typeId": type_id, "identifier": ue.ident, "answers": answers}
        parent = ues[ue.parent_idents[0]] if ue.parent_idents else None
        if parent and parent.id:
            body["parentRecordingUnitId"] = int(parent.id)
        st, r = api.call("POST", "/recording-units", body)
        if st == 201:
            ue.id = r["data"]["id"]
        else:
            fail(f"UE {ue.ident}", st, r)
        if i % 25 == 0:
            print(f"  UE {i}/{len(order)}", flush=True)

    # 2. Parents supplémentaires
    links = {(p, ue.ident) for ue in ues.values() for p in ue.parent_idents[1:]} | set(hier)
    for parent, child in sorted(links):
        p, c = ues.get(parent), ues.get(child)
        if not (p and c and p.id and c.id):
            continue
        st, r = api.call("POST", f"/recording-units/{c.id}/parents", {"relatedRecordingUnitId": int(p.id)})
        if st not in (204, 200):
            fail(f"parent {parent} > {child}", st, r)

    # 3. Relations stratigraphiques
    for a, b, verb in strati:
        ua, ub = ues[a], ues[b]
        if not (ua.id and ub.id):
            continue
        position = STRATI[verb][0]
        st, r = api.call("POST", f"/recording-units/{ua.id}/stratigraphic-relationships",
                         {"relatedRecordingUnitId": int(ub.id), "position": position, "typeConceptId": int(ctx["strati"][verb])})
        if st != 204:
            fail(f"strati {a} {verb} {b}", st, r)

    # 4. Documents : type lu dans th182 (Cahier de fouille, Relevé, Photographie de fouille / d'objet)
    doc_type = {role: ctx["doc"].get(m["th182"]) for role, m in mapping["typeDocument"].items()}
    for n, (code, d) in enumerate(sorted(docs.items()), 1):
        owners = [ues[i] for i in d["ues"] if ues[i].id]
        if not owners:
            continue
        st, r = api.call("POST", "/documents", {
            "projectId": project, "typeId": doc_type[d["role"]], "identifier": code, "title": code,
            "recordingUnitIds": [int(owners[0].id)]})
        if st != 201:
            fail(f"document {code}", st, r)
            continue
        doc_id = r["data"]["id"]
        for ue in owners[1:]:
            st, r = api.call("PUT", f"/recording-units/{ue.id}/documents/{doc_id}")
            if st not in (200, 204):
                fail(f"lien document {code} > {ue.ident}", st, r)
        if n % 100 == 0:
            print(f"  documents {n}/{len(docs)}", flush=True)

    report.append(f"Appels API : {dict(api.calls)}")
    report.append(f"Descriptions tronquées à 5000 caractères : {truncated}")
    for key, c in unmatched.items():
        report.append(f"Valeurs sans concept ({key}) : " + "; ".join(f"{v!r}×{n}" for v, n in c.most_common()))
    report.append(f"Erreurs : {len(errors)}")
    report += [f"  - {e}" for e in errors[:100]]
    Path(args.report).write_text("\n".join(report), encoding="utf-8")
    print("\n".join(report[-(len(errors[:100]) + 2):]))


if __name__ == "__main__":
    sys.exit(main())
