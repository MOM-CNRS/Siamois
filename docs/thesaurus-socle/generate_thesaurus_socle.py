#!/usr/bin/env python3
"""
Génère le thésaurus SIAMOIS « socle commun » (SKOS / RDF-XML, format d'export OpenTheso).

Entrées  : th326 (référentiel national) et th230 (ancien thésaurus SIAMOIS), exportés en RDF.
Sortie   : un RDF prêt à être importé dans OpenTheso comme nouveau thésaurus.

    python3 -I generate_thesaurus_socle.py [--ref th326.rdf] [--old th230.rdf] [--out socle.rdf]

Le modèle est décrit dans README.md. En bref :
  - une table = un concept (notation SIAMOIS#SIAxx) ; un champ = un concept sous la table ;
  - un champ vocabulaire porte la notation SIAMOIS#<code de champ> et ses valeurs en sous-concepts ;
  - un champ sans vocabulaire porte SIAMOISFIELD#<table>.<propriété> (ignoré par le chargeur de l'application) ;
  - une liste qui dépend du type = skos:related entre le type et les valeurs (comme le th230),
    plus une collection générée par couple (champ, type).
"""
import argparse
import glob
import os
import re
import sys
import unicodedata
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

DL = os.path.expanduser("~/Downloads")
ap = argparse.ArgumentParser()
ap.add_argument("--ref", default=(glob.glob(DL + "/Référentiel national*th326*.rdf") or [""])[0])
ap.add_argument("--old", default=(glob.glob(DL + "/Siamois*th230*.rdf") or [""])[0])
ap.add_argument("--out", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "siamois-socle-commun.rdf"))
ap.add_argument("--base", default="https://thesaurus.mom.fr")
ap.add_argument("--th", default="th999", help="identifiant du thésaurus dans les URI (remplacé à l'import)")
ap.add_argument("--first-id", type=int, default=1000)
args = ap.parse_args()

BASE, TH, TODAY, CREATOR = args.base, args.th, "2026-10-23", "gregory.bliault"
NS = {"rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#", "skos": "http://www.w3.org/2004/02/skos/core#",
      "iso": "http://purl.org/iso25964/skos-thes#"}
RDF, SKOS = "{%s}" % NS["rdf"], "{%s}" % NS["skos"]
XML_LANG = "{http://www.w3.org/XML/1998/namespace}lang"
ERRORS = []


# --------------------------------------------------------------------------------------------- sources
def load(path):
    """uri -> {fr, labels, alt, defs, narrower}"""
    out = {}
    for d in ET.parse(path).getroot().findall("rdf:Description", NS):
        types = [t.get(RDF + "resource").split("#")[-1] for t in d.findall("rdf:type", NS)]
        if "Concept" not in types:
            continue
        rec = {"labels": {}, "alt": [], "defs": {}, "narrower": [d2.get(RDF + "resource") for d2 in d.findall("skos:narrower", NS)]}
        for p in d.findall("skos:prefLabel", NS):
            rec["labels"][p.get(XML_LANG)] = p.text
        for p in d.findall("skos:altLabel", NS):
            rec["alt"].append(p.text)
        for p in d.findall("skos:definition", NS):
            rec["defs"].setdefault(p.get(XML_LANG), []).append(p.text)
        rec["fr"] = rec["labels"].get("fr") or next(iter(rec["labels"].values()), "")
        out[d.get(RDF + "about")] = rec
    return out


if not (os.path.exists(args.ref) and os.path.exists(args.old)):
    sys.exit("Sources introuvables : passer --ref et --old")
REF, OLD = load(args.ref), load(args.old)


def find(src, label, with_children=False):
    for uri, r in src.items():
        if r["fr"] == label and (r["narrower"] or not with_children):
            return uri
    return None


def ref_value_uris(field_label):
    """libellé -> uri des valeurs d'un champ du référentiel (th326)."""
    uri = find(REF, field_label, True)
    if uri is None:
        ERRORS.append("th326: champ sans valeurs introuvable : " + field_label)
        return {}
    return {REF[c]["fr"].casefold(): c for c in REF[uri]["narrower"] if c in REF}


def old_descendants(field_label):
    """libellé fr -> enregistrement th230, pour tout le sous-arbre d'un champ."""
    uri = find(OLD, field_label, True)
    out, stack = {}, list(OLD[uri]["narrower"])
    while stack:
        u = stack.pop()
        if u in OLD:
            out.setdefault(OLD[u]["fr"], OLD[u])
            stack.extend(OLD[u]["narrower"])
    return out


# --------------------------------------------------------------------------------------------- modèle
NODES, ORDER = {}, []


def slug(s):
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", "-", s).strip("-")


class Node:
    def __init__(self, key, fr, en=None, notation=None, parent=None):
        assert key not in NODES, "clé en double : " + key
        self.key, self.fr, self.en, self.notation, self.parent = key, fr, en, notation, parent
        self.labels_extra, self.alt, self.defs = {}, [], {}
        self.children, self.related, self.matches, self.groups = [], [], [], []
        self.table = None
        NODES[key] = self
        ORDER.append(self)
        if parent is not None:
            parent.children.append(self)

    def define(self, fr, en=None):
        self.defs["fr"] = fr
        if en:
            self.defs["en"] = en


ROOT = Node("root", "Siamois - Concepts système", "Siamois - System concepts", "SIAMOIS#SIAAUTO")
ROOT.define("Champs du socle commun SIAMOIS : une table par entité, un concept par champ, et pour les champs à vocabulaire la "
            "liste de valeurs proposée en démonstration. Une liste qui dépend du type est portée par des liens « associé » "
            "entre le type et les valeurs qu'il autorise ; des collections « Table · Champ — Type » en donnent la lecture directe.",
            "Fields of the SIAMOIS common core: one table per entity, one concept per field, and for vocabulary fields the "
            "demonstration list of values. A list that depends on the type is carried by 'related' links between the type and "
            "the values it allows; collections 'Table · Field — Type' give the direct reading.")

KIND = {
    "TEXT": "Texte court", "TEXTAREA": "Texte long", "INT": "Nombre entier", "DECIMAL": "Nombre décimal", "DATE": "Date",
    "BOOL": "Case à cocher (oui / non)", "MEASURE": "Mesure avec unité", "ONE": "Liste de vocabulaire, choix unique",
    "MANY": "Liste de vocabulaire, choix multiple", "PERSONS": "Personne(s)", "LINK": "Lien vers une ou plusieurs fiches",
    "FILE": "Fichier", "ADDRESS": "Adresse", "RELATIONS": "Relations stratigraphiques (fiche liée + type de relation)",
}


def ref_field_uri(label):
    return find(REF, label)


def table(code, key, fr, en, defn):
    n = Node(key, fr, en, "SIAMOIS#" + code, ROOT)
    n.define(defn)
    n.table = n
    n.code = code
    return n


def field(tbl, binding, fr, en, kind, code=None, rn=None, note=None, is_type=False):
    notation = ("SIAMOIS#" + code) if code else "SIAMOISFIELD#%s.%s" % (tbl.code, binding)
    n = Node("%s.%s" % (tbl.key, binding), fr, en, notation, tbl)
    n.table, n.kind, n.code, n.is_type = tbl, kind, code, is_type
    parts = [KIND[kind] + "."]
    if code:
        parts.append("Code de champ : %s." % code)
    if is_type:
        parts.append("Type de la fiche : la liste donne les types candidats, chaque projet configure ceux qu'il utilise. "
                     "D'autres listes de cette table peuvent dépendre du type choisi.")
    if note:
        parts.append(note)
    if rn:
        uri = ref_field_uri(rn)
        if uri:
            n.matches.append(uri)
        else:
            ERRORS.append("th326: champ introuvable : " + rn)
        parts.append("Référentiel national : %s." % rn)
    n.define(" ".join(parts))
    return n


def values(fld, text, rnf=None, defs=None):
    """Lignes « slug | fr | en | rn » ; rn : libellé du référentiel, « = » pour le libellé fr, vide si aucun."""
    ref = ref_value_uris(rnf) if rnf else {}
    for line in text.strip().splitlines():
        cells = [c.strip() for c in line.split("|")]
        cells += [""] * (4 - len(cells))
        sl, fr, en, rn = cells[:4]
        n = Node("%s.%s" % (fld.key, sl), fr, en or None, None, fld)
        n.table = fld.table
        if rn:
            rn = fr if rn == "=" else rn
            if rn.casefold() in ref:
                n.matches.append(ref[rn.casefold()])
                if rn != fr:
                    n.alt.append(rn)
            else:
                ERRORS.append("th326: valeur introuvable : %s / %s" % (rnf, rn))
    return fld


def child(fld, sl):
    return NODES["%s.%s" % (fld.key, sl)]


def relate(a, b):
    if b not in a.related:
        a.related.append(b)
    if a not in b.related:
        b.related.append(a)


COLLECTIONS = []  # (fr, en, [members])
DRIVERS = []


def applies(driver, fields):
    """Le type (ou la valeur) rend ces champs pertinents."""
    if driver not in DRIVERS:
        DRIVERS.append(driver)
    for f in fields:
        relate(driver, f)


def scoped(fld, mapping, tbl_label, tbl_label_en):
    """mapping : {slug du pilote: [slugs de valeurs de fld]} ; le pilote est dans fld.driver_field."""
    for driver_slug, vals in mapping.items():
        driver = child(fld.driver_field, driver_slug)
        members = [child(fld, v) for v in vals]
        if driver not in DRIVERS:
            DRIVERS.append(driver)
        for m in members:
            relate(driver, m)
        code = (fld.code or fld.key)
        scope = "%s:%s" % (fld.driver_field.code, driver_slug)
        COLLECTIONS.append((
            "%s · %s — %s  [%s @ %s]" % (tbl_label, fld.fr, driver.fr, code, scope),
            "%s · %s — %s  [%s @ %s]" % (tbl_label_en, fld.en, driver.en or driver.fr, code, scope),
            members))


def copy_old(fld, field_label, labels, tbl_label=None):
    """Copie, depuis le th230, les valeurs `labels` (libellé fr) du sous-arbre d'un champ."""
    pool = old_descendants(field_label)
    for lab in labels:
        if lab not in pool:
            ERRORS.append("th230: valeur introuvable : %s / %s" % (field_label, lab))
            continue
        if "%s.%s" % (fld.key, slug(lab)) in NODES:
            continue
        r = pool[lab]
        n = Node("%s.%s" % (fld.key, slug(lab)), lab, r["labels"].get("en"), None, fld)
        n.table = fld.table
        n.alt = list(r["alt"])
        if r["labels"].get("de"):
            n.labels_extra["de"] = r["labels"]["de"]
        for lang, ds in r["defs"].items():
            n.defs[lang] = ds[0]
        if any("!!!" in d for d in n.defs.values()):
            n.defs.clear()


def periods(fld):
    """Chronologie du référentiel (chronologie_OA), recopiée sous un champ."""
    top = find(REF, "chronologie_OA", True)

    def rec(uri, parent):
        r = REF[uri]
        n = Node("%s.%s" % (fld.key, slug(r["fr"])), r["fr"], None, None, parent)
        n.table = fld.table
        n.matches.append(uri)
        for c in r["narrower"]:
            if c in REF:
                rec(c, n)

    for c in REF[top]["narrower"]:
        if c in REF:
            rec(c, fld)
    return fld


def with_driver(fld, driver_field):
    fld.driver_field = driver_field
    return fld


# ============================================================================== TABLES ET CHAMPS
# ---------------------------------------------------------------------------------------- Projet
AU = table("SIAAU", "au", "TBL_projet", "TBL_project", "Projet (unité d'action) : l'opération ou l'étude qui regroupe les fiches.")
au_type = field(AU, "type", "Type", "Type", "ONE", "SIAAU.TYPE", "type_OA", is_type=True)
field(AU, "name", "Nom", "Name", "TEXT", rn="intitule_OA")
field(AU, "identifier", "Identifiant", "Identifier", "TEXT")
field(AU, "oaCode", "Code OA", "OA code", "TEXT", rn="code_OA_NAT")
field(AU, "status", "Statut du projet", "Project status", "ONE", "SIAAU.STATUS")
field(AU, "beginDate", "Date de début", "Start date", "DATE", rn="date_debut_terrain_OA")
field(AU, "endDate", "Date de fin", "End date", "DATE", rn="date_fin_terrain_OA")
field(AU, "mainLocation", "Commune ou localisation principale", "Main location", "LINK")
field(AU, "spatialContext", "Localisation précise", "Precise location", "LINK")
field(AU, "regionMaritimeFacade", "Région / façade maritime", "Region / maritime front", "TEXT", rn="region_facade_maritime_OA")
field(AU, "scientificManager", "Responsable scientifique", "Scientific manager", "TEXT", rn="responsable_scientifique_OA")
field(AU, "hostStructure", "Structure porteuse / Opérateur", "Host structure / operator", "TEXT", rn="organisme_rattachement_OA")
field(AU, "developer", "Aménageur", "Developer", "TEXT", rn="Maître d'ouvrage des travaux d'aménagement")
field(AU, "developmentNature", "Nature de l'aménagement", "Nature of the development", "ONE", "SIAAU.DEVELOPMENT_NATURE", "Nature de l'aménagement")
field(AU, "prescriptionOrderNumber", "Numéro de l'arrêté de prescription", "Prescription order number", "TEXT", rn="Arrêté de prescription")
field(AU, "prescriptionOrderDate", "Date de l'arrêté de prescription", "Prescription order date", "DATE", rn="Arrêté de prescription")
field(AU, "designationOrderNumber", "Numéro de l'arrêté de désignation", "Designation order number", "TEXT", rn="Acte de désignation du responsable scientifique")
field(AU, "designationOrderDate", "Date de l'arrêté de désignation", "Designation order date", "DATE", rn="Acte de désignation du responsable scientifique")
au_periods = field(AU, "periods", "Périodes", "Periods", "MANY", "SIAAU.PERIODS", "chronologie_OA")
au_subjects = field(AU, "subjects", "Sujets", "Subjects", "MANY", "SIAAU.SUBJECTS", "interpretations_concernees_OA")
field(AU, "scientificNotice", "Notice scientifique", "Scientific notice", "TEXTAREA", rn="III-1 – Notice scientifique (fichier)")
field(AU, "comments", "Commentaires", "Comments", "TEXTAREA", rn="informations_complementaires_OA")
field(AU, "system", "Système", "Coordinate system", "ONE", "SIAAU.SYSTEM")
field(AU, "fieldStatus", "Statut du terrain", "Land status", "ONE", "SIAAU.FIELD_STATUS", "Statut du terrain")
field(AU, "zmin", "Zmin", "Zmin", "DECIMAL", note="Altitude minimale, en mètres (profondeur maximale en milieu sous-marin).")
field(AU, "zmax", "Zmax", "Zmax", "DECIMAL", note="Altitude maximale, en mètres.")
field(AU, "prescribedArea", "Surface prescrite", "Prescribed area", "DECIMAL", rn="Surface prescrite", note="En m².")
field(AU, "excavatedArea", "Surface fouillée", "Excavated area", "DECIMAL", rn="Surface fouillée", note="En m².")
field(AU, "accessibleArea", "Surface accessible", "Accessible area", "DECIMAL", rn="Surface accessible", note="En m².")
field(AU, "openingRate", "Taux d'ouverture", "Opening rate", "DECIMAL", note="En %, par rapport à la surface prescrite.")
field(AU, "volumeCount", "Nombre de volumes", "Number of volumes", "INT")
field(AU, "pageCount", "Nombre de pages", "Number of pages", "INT")
field(AU, "figureCount", "Nombre de figures", "Number of figures", "INT")
field(AU, "appendixCount", "Nombre d'annexes", "Number of appendices", "INT")
field(AU, "georeferencingDocument", "Plan de géoréférencement", "Georeferencing plan", "LINK", rn="code_document_georeferencement_OA",
      note="Un document du projet.")
field(AU, "recordingUnits", "Unités d'enregistrement", "Recording units", "LINK")

values(au_type, """
preventive | Fouille préventive | Rescue excavation | fouille archéologique préventive
diagnostic | Diagnostic | Diagnostic survey | diagnostic archéologique
programmed | Fouille programmée | Research excavation
test-pit | Sondage | Test pit
survey | Prospection | Field survey
building | Étude de bâti | Building survey
desk | Étude documentaire | Desk-based study
""", "type_OA")
values(child(AU, "status"), """
planned | En préparation | Planned
ongoing | En cours | Ongoing
suspended | Suspendu | Suspended
done | Terminé | Completed
repealed | Abrogé | Repealed
""")
values(child(AU, "developmentNature"), """
subdivision | Lotissement | Housing development
business-park | Zone d'activités | Business park
quarry | Carrière / extraction de granulats | Quarry
linear | Infrastructure linéaire (route, voie ferrée) | Linear infrastructure
public-building | Équipement public | Public facility
network | Réseau (canalisation, câble) | Utility network
solar | Parc photovoltaïque | Solar farm
farm | Aménagement agricole | Agricultural works
""")
values(child(AU, "fieldStatus"), """
private | Terrain privé | Private land
state | Domaine public de l'État | State public domain
municipal | Domaine communal | Municipal land
military | Terrain militaire | Military land
listed | Monument historique (classé ou inscrit) | Listed monument
heritage-site | Site patrimonial remarquable | Remarkable heritage site
other | Autre | Other
""")
values(child(AU, "system"), """
l93 | Lambert 93 (EPSG:2154) | Lambert 93 (EPSG:2154)
cc | RGF93 / Conique conforme (CC42 à CC50) | RGF93 / Conic conformal (CC42 to CC50)
wgs84 | WGS 84 (EPSG:4326) | WGS 84 (EPSG:4326)
webmercator | Web Mercator (EPSG:3857) | Web Mercator (EPSG:3857)
l2e | Lambert II étendu (EPSG:27572) | Lambert II extended (EPSG:27572)
local | Système local | Local system
""")
periods(au_periods)
values(au_subjects, """
housing | Habitat | Housing
funerary | Funéraire | Funerary
craft | Artisanat | Craft
agriculture | Agriculture | Agriculture
defence | Défensif / militaire | Defence / military
religion | Religieux | Religious
roads | Voirie et réseaux | Roads and networks
paleoenv | Paléoenvironnement | Palaeoenvironment
undetermined | Indéterminé | Undetermined
""")
with_driver(au_subjects, au_type)
scoped(au_subjects, {
    "preventive": ["housing", "funerary", "craft", "agriculture", "defence", "religion", "roads", "undetermined"],
    "diagnostic": ["housing", "funerary", "roads", "undetermined"],
    "programmed": ["housing", "funerary", "craft", "agriculture", "defence", "religion", "roads", "paleoenv", "undetermined"],
}, "Projet", "Project")
applies(child(au_type, "preventive"), [child(AU, "developmentNature"), child(AU, "prescriptionOrderNumber"), child(AU, "prescriptionOrderDate"),
                                       child(AU, "prescribedArea"), child(AU, "developer")])
applies(child(au_type, "diagnostic"), [child(AU, "developmentNature"), child(AU, "prescriptionOrderNumber"), child(AU, "prescriptionOrderDate"),
                                       child(AU, "prescribedArea"), child(AU, "developer")])

# ------------------------------------------------------------------------------------------ Lieu
SU = table("SIASU", "su", "TBL_lieu", "TBL_place", "Lieu (unité spatiale) : commune, parcelle, lieu-dit, site…")
su_type = field(SU, "type", "Type", "Type", "ONE", "SIASU.TYPE", is_type=True)
field(SU, "name", "Nom", "Name", "TEXT")
field(SU, "code", "Code", "Code", "TEXT")
field(SU, "placeNumber", "Identifiant", "Identifier", "INT")
field(SU, "inseeCode", "Code INSEE", "INSEE code", "TEXT", rn="numero_INSEE_commune_OA")
field(SU, "localityName", "Lieu-dit", "Locality name", "TEXT", rn="lieu_dit_OA")
field(SU, "shomMapNumber", "Numéro de carte Shom", "SHOM chart number", "TEXT", rn="carte_SHOM_numero", note="Milieu sous-marin.")
field(SU, "address", "Adresse", "Address", "ADDRESS", rn="adresse_OA")
field(SU, "comments", "Commentaires", "Comments", "TEXTAREA", rn="informations_complementaires_parcelle")
values(su_type, """
municipality | Commune | Municipality
parcel | Parcelle | Cadastral parcel
locality | Lieu-dit | Locality
address | Adresse | Address
site | Site archéologique | Archaeological site
footprint | Emprise de projet | Project footprint
sea-area | Zone maritime (carte Shom) | Maritime area (SHOM chart)
""")
applies(child(su_type, "sea-area"), [child(SU, "shomMapNumber")])
applies(child(su_type, "municipality"), [child(SU, "inseeCode")])
applies(child(su_type, "parcel"), [child(SU, "localityName")])

# ------------------------------------------------------------------------ Unité d'enregistrement
RU = table("SIARU", "ru", "TBL_enregistrement", "TBL_recording", "Unité d'enregistrement (US, fait, ensemble…).")
ru_type = field(RU, "type", "Type", "Type", "ONE", "SIARU.TYPE", "type_UE", is_type=True)
field(RU, "fullIdentifier", "Identifiant", "Identifier", "TEXT", rn="identifiant_UE")
field(RU, "author", "Auteur scientifique/technique", "Main author", "PERSONS")
field(RU, "contributors", "Contributeur(s) scientifique(s)/technique(s)", "Contributors", "PERSONS")
field(RU, "openingDate", "Date d'ouverture", "Opening date", "DATE")
field(RU, "closingDate", "Date de fermeture", "Closing date", "DATE")
field(RU, "spatialUnit", "Lieu(x)", "Place(s)", "LINK")
field(RU, "actionUnit", "Projet", "Project", "LINK")
ru_nature = field(RU, "geomorphologicalCycle", "Nature", "Nature", "ONE", "SIARU.GEOMORPHO", "nature_UE")
ru_agent = field(RU, "geomorphologicalAgent", "Agent géomorphologique", "Geomorphological agent", "ONE", "SIARU.GEOMORPHOAGENT")
ru_interp = field(RU, "normalizedInterpretation", "Interprétation normalisée", "Standardised interpretation", "ONE", "SIARU.INTERPRETATION",
                  "interpretation_UE")
ru_shape = field(RU, "erosionShape", "Forme de l'érosion", "Erosion shape", "ONE", "SIARU.EROSIONSHAPE")
ru_profile = field(RU, "erosionProfile", "Profil de l'érosion", "Erosion profile", "ONE", "SIARU.EROSIONPROFILE")
ru_orient = field(RU, "erosionOrientation", "Orientation de l'érosion", "Erosion orientation", "ONE", "SIARU.EROSIONORIENTATION")
ru_matrix = field(RU, "matrixColor", "Couleur de la matrice", "Matrix colour", "TEXT")
field(RU, "description", "Description", "Description", "TEXTAREA", rn="description_UE")
ru_chrono = field(RU, "chronologicalPhase", "Attribution chronologique", "Chronological attribution", "ONE", "SIARU.CHRONO", "chronologie_UE")
field(RU, "tpq", "TPQ", "TPQ", "INT", rn="TPQ_UE", note="Terminus post quem (année).")
field(RU, "taq", "TAQ", "TAQ", "INT", rn="TAQ_UE", note="Terminus ante quem (année).")
field(RU, "zSup", "Altitude supérieure", "Upper elevation", "MEASURE", rn="Zsup_UE")
field(RU, "zInf", "Altitude inférieure", "Lower elevation", "MEASURE", rn="Zinf_UE")
field(RU, "phases", "Phases", "Phases", "LINK")
field(RU, "parents", "Fait partie de", "Is part of", "LINK")
field(RU, "children", "Contient", "Contains", "LINK")
field(RU, "specimenList", "Mobilier", "Finds", "LINK")
field(RU, "comments", "Commentaires", "Comments", "TEXTAREA", rn="informations_complementaires_UE")

values(ru_type, """
us | US | Stratigraphic unit (SU) | =
fait | Fait | Feature | =
ensemble | Ensemble | Structured archaeological entity | entité archéologique structurée
uf | UF | Functional unit | =
sepulture | Sépulture | Burial | =
sondage | Sondage | Test pit | =
tranchee | Tranchée | Trench | =
zone | Zone | Area | =
secteur | Secteur | Sector | =
carre | Carré | Grid square | =
""", "type_UE")

# Nature et agent : valeurs du th230, avec leurs définitions.
copy_old(ru_nature, "Nature", ["Dépôt", "Erosion", "Altération", "Composite"])
copy_old(ru_agent, "Agent géomorphologique", ["Agent naturel", "Agent anthropique", "Agent anthropique et naturel", "Agent indéterminé"])
with_driver(ru_nature, ru_type)
scoped(ru_nature, {"us": ["depot", "erosion", "alteration", "composite"], "fait": ["depot", "erosion"],
                   "ensemble": ["depot", "composite"]}, "Enregistrement", "Recording")
applies(child(ru_type, "us"), [ru_nature, ru_agent, ru_matrix])
applies(child(ru_type, "fait"), [ru_nature, ru_agent])
applies(child(ru_type, "ensemble"), [ru_nature, ru_agent])
for sl in ("depot", "alteration", "composite"):
    applies(child(ru_nature, sl), [ru_matrix])
applies(child(ru_nature, "erosion"), [ru_shape, ru_profile, ru_orient])

# Interprétation : liste par type (th230).
INTERP = {
    "us": ["Couche de démolition", "Couche d'occupation", "Epandage", "Remblai", "Remblai de fossé", "Remplissage de tranchée de fondation",
           "Remplissage de tranchée de récupération", "Sol aménagé", "Radier", "Enduit de surface", "Niveau d'incendie", "Effondrement de mur",
           "Dépôt alluvionnaire", "Dépôt colluvionnaire", "Substrat rocheux", "Substrat rocheux altéré", "Pédogénèse, humus",
           "Argile naturelle (altération du substrat)", "Surface d'arasement", "Surface de rue", "Terrasse", "Autre dépôt", "Indéterminé"],
    "fait": ["Fosse", "Fosse de fondation", "Fossé", "Fossé rigole", "Trou de poteau", "Empreinte de poteau", "Tranchée de fondation (de mur)",
             "Tranchée de récupération", "Tranchée de canalisation", "Puits", "Bassin", "Cave", "Ornière", "Foyer simple", "Fondation maçonnée",
             "Mur maçonné", "Canalisation", "Hypocauste", "Inhumation", "Restes d'incinération", "Voie aménagée", "Autre érosion", "Indéterminé"],
    "ensemble": ["Bâtiment", "Foyer complexe ou four", "Armature de poutres", "Ensemble clos", "Murus gallicus", "Structure de soutien maçonnée",
                 "Autre incluante", "Indéterminé"],
    "sepulture": ["Inhumation", "Restes d'incinération", "Indéterminé"],
}
copy_old(ru_interp, "Interprétation normalisée", sorted({v for vs in INTERP.values() for v in vs}))
with_driver(ru_interp, ru_type)
scoped(ru_interp, {t: [slug(v) for v in vs] for t, vs in INTERP.items()}, "Enregistrement", "Recording")

values(ru_shape, """
circular | Circulaire | Circular
oval | Ovale | Oval
rectangular | Rectangulaire | Rectangular
square | Carrée | Square
irregular | Irrégulière | Irregular
linear | Linéaire | Linear
""")
values(ru_profile, """
u | En U | U-shaped
v | En V | V-shaped
flat | À fond plat | Flat-bottomed
basin | En cuvette | Basin-shaped
vertical | À parois verticales | Vertical-sided
irregular | Irrégulier | Irregular
""")
values(ru_orient, """
ns | Nord-Sud | North-South
ew | Est-Ouest | East-West
nesw | Nord-Est / Sud-Ouest | North-East / South-West
nwse | Nord-Ouest / Sud-Est | North-West / South-East
none | Sans orientation | None
""")
periods(ru_chrono)

# ------------------------------------------------------------------------------ Stratigraphie
ST = table("SIARU.STRATI", "st", "TBL_stratigraphie", "TBL_stratigraphy",
           "Relations entre unités d'enregistrement. SIARU.STRATI est la liste commune ; elle sert de repli aux trois listes par côté tant "
           "que le thésaurus ne les définit pas.")
ST.notation = "SIAMOIS#SIARU.STRATI.TABLE"
st_all = field(ST, "all", "Type de relation", "Relation type", "ONE", "SIARU.STRATI", "type_relation_UE")
values(st_all, """
on | sur | over | sur
under | sous | under | sous
equals | équivalent | equivalent | équivalent
cuts | coupe | cuts | coupe
cut-by | est coupée par | is cut by | est coupée par
groups | regroupe | groups | regroupe
grouped-in | est regroupée dans | is grouped in | est regroupée dans
encloses | englobe | encloses | englobe
enclosed-by | est englobée par | is enclosed by | est englobée par
""", "type_relation_UE")
st_ant = field(ST, "anterior", "Relation vers une unité antérieure", "Relation to an earlier unit", "RELATIONS", "SIARU.STRATI.ANTERIOR",
               note="Lue depuis la fiche postérieure : « cette unité coupe / recouvre l'unité antérieure ».")
values(st_ant, """
cuts | coupe | cuts | coupe
on | recouvre | overlies | sur
leans | s'appuie sur | leans on
fills | comble | fills
""", "type_relation_UE")
st_post = field(ST, "posterior", "Relation vers une unité postérieure", "Relation to a later unit", "RELATIONS", "SIARU.STRATI.POSTERIOR",
                note="Lue depuis la fiche antérieure : « cette unité est coupée / recouverte par l'unité postérieure ».")
values(st_post, """
cut-by | est coupée par | is cut by | est coupée par
under | est recouverte par | is overlain by | sous
supports | est appuyée par | is leant on by
filled-by | est comblée par | is filled by
""", "type_relation_UE")
st_sync = field(ST, "synchronous", "Relation vers une unité synchrone", "Relation to a contemporary unit", "RELATIONS",
                "SIARU.STRATI.SYNCHRONOUS", note="Relation symétrique : la fiche est son propre miroir.")
values(st_sync, """
equals | équivalent à | equivalent to | équivalent
contemporary | contemporain de | contemporary with
linked | se lie à | bonded with
""", "type_relation_UE")

# --------------------------------------------------------------------------------------- Mobilier
SP = table("SIAS", "sp", "TBL_mobilier", "TBL_finds", "Mobilier : objet, lot, individu (VAB), prélèvement.")
sp_type = field(SP, "type", "Type", "Type", "ONE", "SIAS.CAT", is_type=True,
                note="Deux familles de types coexistent : la nature du mobilier (céramique, métal…) et le conditionnement exigé par le "
                     "référentiel (objet, lot, vestige anthropobiologique, prélèvement). Deux collections les distinguent.")
field(SP, "fullIdentifier", "Identifiant", "Identifier", "TEXT", rn="identifiant_objet-lot")
field(SP, "otherIdentifier", "Autre identifiant", "Other identifier", "TEXT")
field(SP, "isolationNumber", "Identifiant d'isolation", "Isolation number", "TEXT")
field(SP, "recordingUnit", "Unité d'enregistrement", "Recording unit", "LINK", rn="code_UE")
field(SP, "parents", "Fait partie de", "Is part of", "LINK")
field(SP, "children", "Contient", "Contains", "LINK")
field(SP, "authors", "Auteurs", "Authors", "PERSONS")
field(SP, "collectors", "Collecteurs", "Collectors", "PERSONS")
field(SP, "collectionDate", "Date de collecte", "Collection date", "DATE", rn="date_decouverte_objet-lot")
sp_mat = field(SP, "material", "Matière", "Material", "MANY", "SIAS.MATIERE", "matiere")
sp_class = field(SP, "materialClass", "Classe", "Class", "MANY", "SIAS.CLASS", "classe")
sp_interp = field(SP, "normalizedInterpretation", "Interprétation", "Interpretation", "ONE", "SIAS.INTERPRETATION", "determination_objet-lot")
sp_method = field(SP, "method", "Méthode de prélèvement", "Sampling method", "ONE", "SIAS.METHOD")
field(SP, "description", "Description", "Description", "TEXTAREA")
field(SP, "comments", "Commentaires", "Comments", "TEXTAREA", rn="informations_complementaires_objet-lot")
sp_chrono = field(SP, "chronologicalAttribution", "Attribution chronologique", "Chronological attribution", "ONE", "SIAS.CHRONO",
                  "chronologie_objet-lot")
field(SP, "tpq", "TPQ", "TPQ", "INT", rn="TPQ_objet-lot")
field(SP, "taq", "TAQ", "TAQ", "INT")
field(SP, "numberOfElements", "Nombre d'éléments", "Number of elements", "INT", rn="nombre_element_objet-lot")
field(SP, "weight", "Poids", "Weight", "MEASURE", rn="poids_objet-lot")
field(SP, "containers", "Contenant", "Container", "LINK", rn="identifiant_contenant")
field(SP, "phases", "Phases", "Phases", "LINK")
field(SP, "actionUnit", "Projet", "Project", "LINK")
sp_sanitary = field(SP, "sanitaryState", "État sanitaire", "Sanitary state", "ONE", "SIAS.SANITARY", "etat_sanitaire_objet-lot")
sp_alert = field(SP, "sanitaryAlert", "Alerte sanitaire", "Sanitary alert", "TEXTAREA", rn="alerte_sanitaire_objet-lot")
sp_transfer = field(SP, "transferStatus", "Versement", "Transfer to the State", "ONE", "SIAS.TRANSFER", "versement_objet-lot")
sp_select = field(SP, "selectiveConservation", "Conservation sélective", "Selective conservation", "ONE", "SIAS.SELECTIVECONSERVATION",
                  "proposition_conservation_selective_objet-lot")
field(SP, "selectiveConservationComment", "Commentaire de conservation sélective", "Selective conservation comment", "TEXTAREA",
      rn="commentaire_proposition_conservation_selective_objet-lot")
field(SP, "particularInterest", "Intérêt particulier", "Particular interest", "TEXTAREA", rn="interet_particulier_objet-lot")
sp_potential = field(SP, "studyPotential", "Potentiel d'étude", "Study potential", "BOOL", rn="potentiel_etude_VAB")
sp_potential_c = field(SP, "studyPotentialComment", "Commentaire du potentiel d'étude", "Study potential comment", "TEXTAREA",
                       rn="commentaire_potentiel_etude_VAB")
sp_purpose = field(SP, "samplingPurpose", "Objectif du prélèvement", "Sampling purpose", "TEXTAREA", rn="objectif_prelevement")
sp_precond = field(SP, "preservationCondition", "Condition de préservation", "Preservation condition", "TEXTAREA",
                   rn="condition_preservation_prelevement")
sp_valid = field(SP, "validityDate", "Date de validité", "Validity date", "DATE", rn="date_validite_prelevement")
field(SP, "storageLocation", "Lieu de conservation", "Storage location", "TEXT", rn="lieu_de_conservation")

values(sp_type, """
ceramique | Céramique | Pottery
metal | Métal | Metal
lithique | Industrie lithique | Lithic industry
verre | Verre | Glass
faune | Faune | Fauna
tca | Terre cuite architecturale | Architectural terracotta
vab | Vestige anthropobiologique (VAB) | Anthropobiological remains
prelevement | Prélèvement | Sample
objet | Objet | Object | objet
lot | Lot | Lot | lot
""", "objet-lot")
values(sp_mat, """
organique | organique | organic | =
mineral | minéral | mineral | =
terre-crue | terre crue | raw earth | =
terre-cuite | terre cuite | fired clay | =
metal | métal | metal | =
verrerie | verrerie | glass | =
synthetique | synthétique | synthetic | =
autre | autre | other | =
indetermine | indéterminé | undetermined | =
""", "matiere")
values(sp_class, """
org-vab | Vestiges anthropobiologiques (organique) | Anthropobiological remains (organic) | [organique] [vestiges anthropobiologiques]
org-travaille | Travaillé (organique) | Worked (organic) | [organique] travaillé
org-non-travaille | Non travaillé (organique) | Unworked (organic) | [organique] non travaillé
org-indetermine | Indéterminé (organique) | Undetermined (organic) | [organique] indéterminé
min-lithique | Lithique (minéral) | Lithic (mineral) | [minéral] lithique
min-lapidaire | Lapidaire (minéral) | Stonework (mineral) | [minéral] lapidaire
min-autre | Autre (minéral) | Other (mineral) | [minéral] autre
crue-architectural | Architectural (terre crue) | Architectural (raw earth) | [terre crue] architectural
crue-autre | Autre (terre crue) | Other (raw earth) | [terre crue] autre
cuite-ceramique | Céramique (terre cuite) | Pottery (fired clay) | [terre cuite] céramique
cuite-architectural | Architectural (terre cuite) | Architectural (fired clay) | [terre cuite] architectural
cuite-autre | Autre (terre cuite) | Other (fired clay) | [terre cuite] autre
metal-monetaire | Monétaire (métal) | Coinage (metal) | [métal] monétaire
metal-ferreux | Ferreux (métal) | Ferrous (metal) | [métal] ferreux
metal-cuivreux | Alliage cuivreux (métal) | Copper alloy (metal) | [métal] alliage cuivreux
metal-autre | Autre (métal) | Other (metal) | [métal] autre
verre-vaisselle | Vaisselle (verrerie) | Tableware (glass) | [verrerie] vaisselle
verre-contenant | Contenant (verrerie) | Container (glass) | [verrerie] contenant
verre-architectural | Architectural (verrerie) | Architectural (glass) | [verrerie] architectural
verre-autre | Autre (verrerie) | Other (glass) | [verrerie] autre
autre-revetement | Revêtement décoratif (autre) | Decorative coating (other) | [autre] revêtement décoratif
autre-architectural | Architectural (autre) | Architectural (other) | [autre] architectural
autre-autre | Autre (autre) | Other (other) | [autre] autre
""", "classe")
values(sp_interp, """
vase | Vase | Vessel
bowl | Jatte / bol | Bowl
amphora | Amphore | Amphora
jug | Cruche / pichet | Jug
pot | Marmite | Cooking pot
lamp | Lampe | Lamp
spindle-whorl | Fusaïole | Spindle whorl
coin | Monnaie | Coin
brooch | Fibule | Brooch
nail | Clou | Nail
tool | Outil | Tool
weapon | Arme | Weapon
jewel | Bijou | Jewellery
flake-tool | Outil sur éclat | Flake tool
blade | Lame / lamelle | Blade / bladelet
core | Nucléus | Core
polished-axe | Hache polie | Polished axe
quern | Meule | Quern
debitage | Débitage | Debitage
cup | Gobelet | Cup
flask | Flacon / balsamaire | Flask / unguentarium
bead | Perle | Bead
bracelet | Bracelet | Bracelet
window | Vitre | Window glass
food-waste | Déchet alimentaire | Food waste
worked-bone | Os travaillé | Worked bone
animal-burial | Dépôt animal | Animal deposit
skeleton | Squelette en connexion | Articulated skeleton
disarticulated | Ossements en vrac | Disarticulated bones
cremation | Crémation | Cremation
teeth | Dents isolées | Isolated teeth
sediment | Sédiment | Sediment
charcoal | Charbons de bois | Charcoal
wood | Bois | Wood
seeds | Restes carpologiques | Plant macro-remains
mortar | Mortier | Mortar
undetermined | Indéterminé | Undetermined
""")
values(sp_method, """
spot | Prélèvement ponctuel | Spot sample
column | Colonne / carottage | Column / core
block | Bloc non perturbé | Undisturbed block
sieving | Tamisage | Sieving
flotation | Flottation | Flotation
""")
values(sp_sanitary, """
stable | stable | stable | =
stabilised | stabilisé | stabilised | stabilisé
unstable | instable | unstable | =
critical | critique | critical | =
""", "etat_sanitaire_objet-lot")
values(sp_transfer, """
yes | oui | yes | =
returned | déjà remis | already returned | =
not-kept | non conservé | not kept | =
lost | disparu | lost | =
treatment | traitement | under treatment | =
""", "versement_objet-lot")
values(sp_select, """
keep | conservation | keep | =
discard | non conservation | do not keep | =
""", "proposition_conservation_selective_objet-lot")
periods(sp_chrono)

with_driver(sp_mat, sp_type)
scoped(sp_mat, {"ceramique": ["terre-cuite"], "tca": ["terre-cuite"], "metal": ["metal"], "lithique": ["mineral"], "verre": ["verrerie"],
                "faune": ["organique"], "vab": ["organique"], "prelevement": ["organique", "mineral", "indetermine"]}, "Mobilier", "Finds")
with_driver(sp_class, sp_type)
scoped(sp_class, {"ceramique": ["cuite-ceramique"], "tca": ["cuite-architectural", "cuite-autre"],
                  "metal": ["metal-monetaire", "metal-ferreux", "metal-cuivreux", "metal-autre"],
                  "lithique": ["min-lithique", "min-lapidaire", "min-autre"],
                  "verre": ["verre-vaisselle", "verre-contenant", "verre-architectural", "verre-autre"],
                  "faune": ["org-travaille", "org-non-travaille", "org-indetermine"], "vab": ["org-vab"]}, "Mobilier", "Finds")
with_driver(sp_interp, sp_type)
scoped(sp_interp, {
    "ceramique": ["vase", "bowl", "amphora", "jug", "pot", "lamp", "spindle-whorl", "undetermined"],
    "metal": ["coin", "brooch", "nail", "tool", "weapon", "jewel", "undetermined"],
    "lithique": ["flake-tool", "blade", "core", "polished-axe", "quern", "debitage", "undetermined"],
    "verre": ["cup", "flask", "bead", "bracelet", "window", "undetermined"],
    "faune": ["food-waste", "worked-bone", "animal-burial", "undetermined"],
    "vab": ["skeleton", "disarticulated", "cremation", "teeth", "undetermined"],
    "prelevement": ["sediment", "charcoal", "wood", "seeds", "mortar", "undetermined"],
}, "Mobilier", "Finds")
# La matière pilote aussi la classe (lecture référentiel : « classe » dépend de « matière »).
with_driver(sp_class, sp_mat)
scoped(sp_class, {"organique": ["org-vab", "org-travaille", "org-non-travaille", "org-indetermine"],
                  "mineral": ["min-lithique", "min-lapidaire", "min-autre"], "terre-crue": ["crue-architectural", "crue-autre"],
                  "terre-cuite": ["cuite-ceramique", "cuite-architectural", "cuite-autre"],
                  "metal": ["metal-monetaire", "metal-ferreux", "metal-cuivreux", "metal-autre"],
                  "verrerie": ["verre-vaisselle", "verre-contenant", "verre-architectural", "verre-autre"],
                  "autre": ["autre-revetement", "autre-architectural", "autre-autre"]}, "Mobilier", "Finds")
applies(child(sp_type, "prelevement"), [sp_method, sp_purpose, sp_precond, sp_valid])
applies(child(sp_type, "vab"), [sp_potential, sp_potential_c])
applies(child(sp_type, "metal"), [sp_sanitary, sp_alert])
applies(child(sp_type, "faune"), [sp_sanitary, sp_alert])
applies(child(sp_type, "vab"), [sp_sanitary, sp_alert])

# ------------------------------------------------------------------------------------------ Étude
SD = table("SIAST", "sd", "TBL_etude", "TBL_study", "Étude : traitement, datation, analyse, étude spécialisée.")
sd_type = field(SD, "type", "Type", "Type", "ONE", "SIAST.TYPE", is_type=True)
sd_method = field(SD, "method", "Méthode", "Method", "ONE", "SIAST.METHODE")
sd_treat = field(SD, "treatmentType", "Type de traitement", "Treatment type", "ONE", "SIAST.TYPETRAIT", "type_traitement")
field(SD, "actionUnit", "Projet", "Project", "LINK")
field(SD, "recordingUnits", "Unités d'enregistrement", "Recording units", "LINK")
field(SD, "specimens", "Mobilier", "Finds", "LINK", rn="code_element_traite")
values(sd_type, """
treatment | Traitement | Treatment
dating | Datation | Dating
analysis | Analyse | Analysis
specialist | Étude spécialisée | Specialist study
""")
values(sd_method, """
cleaning | Nettoyage / lavage | Cleaning / washing
imaging | Imagerie (radiographie, scanner) | Imaging (X-ray, CT)
product | Application d'un produit | Product application
c14 | Radiocarbone (14C) | Radiocarbon (14C)
dendro | Dendrochronologie | Dendrochronology
archaeomag | Archéomagnétisme | Archaeomagnetism
luminescence | Luminescence (TL / OSL) | Luminescence (TL / OSL)
typochrono | Typo-chronologie | Typo-chronology
petrography | Pétrographie | Petrography
xrf | Archéométrie (XRF) | Archaeometry (XRF)
micromorph | Micromorphologie | Micromorphology
palynology | Palynologie | Palynology
anthracology | Anthracologie | Anthracology
carpology | Carpologie | Carpology
ceramology | Étude céramologique | Pottery study
fauna | Étude de la faune | Faunal study
anthropology | Étude anthropologique | Anthropological study
lithics | Étude lithique | Lithic study
numismatics | Étude numismatique | Numismatic study
""")
values(sd_treat, """
stabilisation | stabilisation | stabilisation | =
consolidation | consolidation | consolidation | =
dna | analyse ADN | DNA analysis | =
paste | étude de pâte ou de pigment | paste or pigment study | =
marking | marquage | marking | =
xray | radiographie | radiography | =
prep | mise en état pour étude | preparation for study | =
refit | remontage | refitting | =
""", "type_traitement")
with_driver(sd_method, sd_type)
scoped(sd_method, {
    "treatment": ["cleaning", "imaging", "product"],
    "dating": ["c14", "dendro", "archaeomag", "luminescence", "typochrono"],
    "analysis": ["petrography", "xrf", "micromorph", "palynology", "anthracology", "carpology"],
    "specialist": ["ceramology", "fauna", "anthropology", "lithics", "numismatics"]}, "Étude", "Study")
applies(child(sd_type, "treatment"), [sd_treat])

# -------------------------------------------------------------------------------------- Document
DC = table("SIAD", "dc", "TBL_document", "TBL_document", "Document : pièce de la documentation archéologique.")
dc_cat = field(DC, "category", "Catégorie", "Category", "ONE", "SIAD.CATEGORY", "categorie_documentation", is_type=True)
dc_type = field(DC, "documentType", "Type de document", "Document type", "ONE", "SIAD.TYPE", "type_documentation")
dc_nature = field(DC, "supportNature", "Nature du support", "Support nature", "MANY", "SIAD.NATURE", "nature_support_documentation")
dc_format = field(DC, "format", "Format du fichier", "File format", "ONE", "SIAD.FORMAT", "format_documentation")
dc_scale = field(DC, "scale", "Échelle", "Scale", "ONE", "SIAD.SCALE")
dc_lang = field(DC, "language", "Langue", "Language", "ONE", "SIAD.LANGUAGE")
dc_kw = field(DC, "keywords", "Mots-clés", "Keywords", "MANY", "SIAD.KEYWORD")
field(DC, "name", "Nom du document", "Document name", "TEXT", rn="titre_documentation")
field(DC, "actionUnit", "Projet", "Project", "LINK")
field(DC, "authors", "Auteurs", "Authors", "PERSONS", rn="auteur_documentation")
field(DC, "contributors", "Contributeurs", "Contributors", "PERSONS", rn="contributeur_documentation")
field(DC, "publisher", "Éditeur", "Publisher", "TEXT", rn="editeur_documentation")
field(DC, "productionDate", "Date de réalisation", "Production date", "DATE", rn="date_realisation_documentation")
field(DC, "caption", "Description / légende", "Description / caption", "TEXTAREA", rn="description_legende_documentation")
field(DC, "itemCount", "Nombre d'éléments", "Number of items", "INT", rn="nombre_element_documentation")
field(DC, "sizeMb", "Taille (Mo)", "Size (MB)", "DECIMAL")
field(DC, "file", "Fichier", "File", "FILE")
field(DC, "storageLocation", "Lieu de conservation", "Storage location", "TEXT", rn="lieu_de_conservation")
dc_cast = field(DC, "castComment", "Commentaire de moulage", "Cast comment", "TEXTAREA", rn="commentaire_moulage")
field(DC, "recordingUnits", "Unités d'enregistrement", "Recording units", "LINK", rn="code_element_associe")
field(DC, "specimens", "Mobilier", "Finds", "LINK")
field(DC, "places", "Lieux", "Places", "LINK")
field(DC, "phases", "Phases", "Phases", "LINK")
field(DC, "containers", "Contenants", "Containers", "LINK")

values(dc_cat, """
written | Document écrit et base de données | Written document and database | document écrit alpha-numérique et base de données
graphic | Document graphique | Graphic document | document graphique
photo | Document photographique et audiovisuel | Photographic and audiovisual document | document photographique et audiovisuel
cast | Moulage | Cast | moulage
other | Autre document | Other document | autre document
""", "categorie_documentation")
values(dc_type, """
field-book | carnet de fouille | field notebook | =
study-report | rapport étude | study report | =
su-sheet | fiche d'UE | recording unit sheet | fiche d’UE
find-sheet | fiche d'objet | find sheet | fiche d’objet
conservation-sheet | fiche de conservation par individu | conservation sheet per individual | =
strat-diagram | diagramme stratigraphique | stratigraphic diagram | =
points-list | liste de points topographiques | list of survey points | =
database | base de données | database | =
map | carte | map | =
aerial | vue aérienne | aerial view | =
topo-plan | plan topographique | topographic plan | =
field-drawing | relevé de terrain | field drawing | =
rock-art | relevé d'art rupestre | rock art recording | relevé d’art rupestre
object-drawing | dessin d'objet | object drawing | dessin d’objet
traces | relevé des traces d'activités humaines et animales | recording of human and animal traces | relevé des traces d’activités humaines et animales
xray | radiographie | radiograph | =
field-photo | photographie de terrain | field photograph | =
rectified-photo | photographie redressée | rectified photograph | =
object-photo | photographie d'objet | object photograph | photographie d’objet
orthophoto | orthophotographie | orthophotograph | =
audio | enregistrement audio | audio recording | =
video | enregistrement vidéo | video recording | =
imprint | empreinte | imprint | =
print | tirage | cast print | =
section-peel | arraché de coupe | section peel | =
mould | moule | mould | =
point-cloud | nuage de points | point cloud | =
model-3d | modèle 3D | 3D model | =
prototype | prototypage | prototype | =
""", "type_documentation")
values(dc_nature, """
paper | papier | paper | =
graph-paper | papier millimétré | graph paper | =
tracing | calque végétal | tracing paper | =
counter-tracing | contre-calque | counter-tracing | =
polyester | polyester | polyester | =
graph-polyester | polyester millimétré | graph polyester | =
polythene | film polyane | polythene film | =
neg-color | négatif couleur | colour negative | =
neg-bw | négatif n et b | black and white negative | =
slide | diapositive | slide | =
print | tirage | print | =
elastomer | élastomère | elastomer | =
plaster | plâtre | plaster | =
resin | résine | resin | =
digital | numérique | digital | =
""", "nature_support_documentation")
values(dc_format, """
pdf | PDF / PDF-A | PDF / PDF-A
docx | DOCX | DOCX
odt | ODT | ODT
csv | CSV | CSV
xlsx | XLSX | XLSX
gpkg | GeoPackage | GeoPackage
svg | SVG | SVG
dxf | DXF | DXF
dwg | DWG | DWG
geotiff | GeoTIFF | GeoTIFF
tiff | TIFF | TIFF
jpeg | JPEG | JPEG
dng | DNG (RAW) | DNG (RAW)
png | PNG | PNG
mp4 | MP4 | MP4
wav | WAV | WAV
obj | OBJ | OBJ
ply | PLY | PLY
glb | glTF / GLB | glTF / GLB
e57 | E57 | E57
laz | LAS / LAZ | LAS / LAZ
stl | STL | STL
""")
values(dc_scale, """
1-1 | 1/1 | 1:1
1-2 | 1/2 | 1:2
1-5 | 1/5 | 1:5
1-10 | 1/10 | 1:10
1-20 | 1/20 | 1:20
1-50 | 1/50 | 1:50
1-100 | 1/100 | 1:100
1-200 | 1/200 | 1:200
1-500 | 1/500 | 1:500
""")
values(dc_lang, """
fr | Français | French
en | Anglais | English
de | Allemand | German
es | Espagnol | Spanish
it | Italien | Italian
la | Latin | Latin
other | Autre | Other
""")
values(dc_kw, """
stratigraphy | Stratigraphie | Stratigraphy
architecture | Architecture | Architecture
pottery | Céramique | Pottery
funerary | Funéraire | Funerary
environment | Environnement | Environment
survey | Topographie | Survey
dating | Datation | Dating
conservation | Conservation | Conservation
""")
with_driver(dc_type, dc_cat)
scoped(dc_type, {
    "written": ["field-book", "study-report", "su-sheet", "find-sheet", "conservation-sheet", "strat-diagram", "points-list", "database"],
    "graphic": ["map", "aerial", "topo-plan", "field-drawing", "rock-art", "object-drawing", "traces", "strat-diagram"],
    "photo": ["xray", "field-photo", "rectified-photo", "object-photo", "orthophoto", "audio", "video"],
    "cast": ["imprint", "print", "section-peel", "mould"],
    "other": ["point-cloud", "model-3d", "prototype"]}, "Document", "Document")
with_driver(dc_nature, dc_cat)
scoped(dc_nature, {
    "written": ["paper", "digital"],
    "graphic": ["paper", "graph-paper", "tracing", "counter-tracing", "polyester", "graph-polyester", "polythene", "digital"],
    "photo": ["neg-color", "neg-bw", "slide", "print", "digital"],
    "cast": ["elastomer", "plaster", "resin"],
    "other": ["digital", "resin"]}, "Document", "Document")
with_driver(dc_format, dc_cat)
scoped(dc_format, {
    "written": ["pdf", "docx", "odt", "csv", "xlsx", "gpkg"],
    "graphic": ["pdf", "svg", "dxf", "dwg", "geotiff", "tiff"],
    "photo": ["jpeg", "tiff", "dng", "png", "mp4", "wav"],
    "other": ["obj", "ply", "glb", "e57", "laz", "stl"]}, "Document", "Document")
with_driver(dc_scale, dc_cat)
scoped(dc_scale, {"graphic": ["1-1", "1-2", "1-5", "1-10", "1-20", "1-50", "1-100", "1-200", "1-500"]}, "Document", "Document")
applies(child(dc_cat, "graphic"), [dc_scale])
applies(child(dc_cat, "cast"), [dc_cast])

# ------------------------------------------------------------------------------------ Contenant
CT = table("SIAC", "ct", "TBL_contenant", "TBL_container", "Contenant : caisse, sachet, boîte… où le mobilier et la documentation sont rangés.")
ct_type = field(CT, "type", "Type", "Type", "ONE", "SIAC.TYPE", "type-taille_contenant", is_type=True)
field(CT, "identifier", "Identifiant", "Identifier", "TEXT", rn="identifiant_contenant")
field(CT, "spatialUnit", "Lieu", "Place", "LINK", rn="lieu_de_conservation")
field(CT, "actionUnit", "Projet", "Project", "LINK")
field(CT, "length", "Longueur", "Length", "MEASURE")
field(CT, "width", "Largeur", "Width", "MEASURE")
field(CT, "height", "Hauteur", "Height", "MEASURE")
field(CT, "weight", "Poids", "Weight", "MEASURE", rn="poids_contenant")
field(CT, "specimens", "Mobilier", "Finds", "LINK")
values(ct_type, """
pallet | palette | pallet | =
euro-crate | caisse norme Europe | Euro crate | =
clear-box | boîte hermétique transparente | clear airtight box | =
folder | chemise | folder | =
archive-box | boîte à archives | archive box | =
drawing-box | carton à dessins | drawing box | =
audio-tape | cassette audio | audio tape | =
video-tape | cassette vidéo | video tape | =
dvd | DVD | DVD | =
sd-card | carte SD | SD card | =
server | adresse du serveur | server address | =
bag | sachet | bag
""", "type-taille_contenant")

# ---------------------------------------------------------------------------------------- Phase
PH = table("SIAPHASE", "ph", "TBL_phase", "TBL_phase", "Phase : regroupement chronologique d'unités d'enregistrement.")
ph_type = field(PH, "type", "Type", "Type", "ONE", "SIAPHASE.TYPE", is_type=True)
field(PH, "identifier", "Identifiant", "Identifier", "TEXT")
field(PH, "title", "Titre", "Title", "TEXT")
field(PH, "description", "Description", "Description", "TEXTAREA")
field(PH, "orderNumber", "Numéro d'ordre", "Order number", "INT")
field(PH, "lowerBound", "Borne inférieure", "Lower bound", "INT")
field(PH, "upperBound", "Borne supérieure", "Upper bound", "INT")
ph_periods = field(PH, "periods", "Périodes", "Periods", "MANY", "SIAPHASE.PERIOD", "chronologie_OA")
ph_kw = field(PH, "keywords", "Mots-clés", "Keywords", "MANY", "SIAPHASE.KEYWORD")
field(PH, "actionUnit", "Projet", "Project", "LINK")
field(PH, "recordingUnits", "Unités d'enregistrement", "Recording units", "LINK")
values(ph_type, """
occupation | Phase d'occupation | Occupation phase
building | Phase de construction | Building phase
alteration | Phase d'aménagement ou de transformation | Alteration phase
abandon | Phase d'abandon ou de destruction | Abandonment or destruction phase
""")
periods(ph_periods)
values(ph_kw, """
housing | Habitat | Housing
funerary | Funéraire | Funerary
craft | Artisanat | Craft
defence | Défensif | Defence
agriculture | Agriculture | Agriculture
""")

# ------------------------------------------------------------------------------------- Personne
PE = table("SIAP", "pe", "TBL_personne", "TBL_person", "Personne intervenant dans un projet.")
pe_role = field(PE, "role", "Rôle dans l'équipe", "Team role", "ONE", "SIAP.ROLE")
values(pe_role, """
manager | Responsable scientifique | Scientific manager
sector | Responsable de secteur | Sector supervisor
excavator | Fouilleur | Excavator
surveyor | Topographe | Surveyor
draughtsman | Dessinateur | Draughtsman
photographer | Photographe | Photographer
specialist | Spécialiste | Specialist
anthropologist | Anthropologue | Anthropologist
zooarchaeologist | Archéozoologue | Zooarchaeologist
geoarchaeologist | Géoarchéologue | Geoarchaeologist
conservator | Conservateur-restaurateur | Conservator
volunteer | Stagiaire ou bénévole | Intern or volunteer
""")

# -------------------------------------------------------------------------------------- Mesure
MD = table("SIAMD", "md", "TBL_definition_de_mesure", "TBL_measurement_definition", 'Définition des champs additionnels de type « mesure ».')
md_type = field(MD, "type", "Type de mesure", "Measurement type", "ONE", "SIAMD.TYPE")
md_nature = field(MD, "nature", "Nature de la mesure", "Measurement nature", "ONE", "SIAMD.NATURE")
values(md_type, """
dimension | Dimension | Dimension
elevation | Altitude | Elevation
mass | Masse | Mass
volume | Volume | Volume
angle | Angle | Angle
""")
values(md_nature, """
length | Longueur | Length
width | Largeur | Width
height | Hauteur | Height
thickness | Épaisseur | Thickness
diameter | Diamètre | Diameter
depth | Profondeur | Depth
lower | Altitude inférieure | Lower elevation
upper | Altitude supérieure | Upper elevation
mean | Altitude moyenne | Mean elevation
weight | Poids | Weight
dry-weight | Poids sec | Dry weight
capacity | Contenance | Capacity
azimuth | Azimut | Azimuth
dip | Pendage | Dip
""")
with_driver(md_nature, md_type)
scoped(md_nature, {"dimension": ["length", "width", "height", "thickness", "diameter", "depth"],
                   "elevation": ["lower", "upper", "mean"], "mass": ["weight", "dry-weight"], "volume": ["capacity"],
                   "angle": ["azimuth", "dip"]}, "Mesure", "Measurement")

# Familles de types de mobilier : deux collections lisibles dans OpenTheso.
COLLECTIONS.append(("Mobilier · Type — par nature du mobilier  [SIAS.CAT @ nature]", "Finds · Type — by nature of the find  [SIAS.CAT @ nature]",
                    [child(sp_type, s) for s in ("ceramique", "metal", "lithique", "verre", "faune", "tca")]))
COLLECTIONS.append(("Mobilier · Type — conditionnement du référentiel  [SIAS.CAT @ referentiel]",
                    "Finds · Type — conditioning required by the national referential  [SIAS.CAT @ referential]",
                    [child(sp_type, s) for s in ("objet", "lot", "vab", "prelevement")]))

# ============================================================================== SÉRIALISATION
ids = {}
for i, n in enumerate(ORDER):
    ids[n.key] = args.first_id + i
URI = {n.key: "%s/?idc=%d&idt=%s" % (BASE, ids[n.key], TH) for n in ORDER}
SCHEME = "%s/api/ark:/66666/%s" % (BASE, TH)
GROUP_FIRST = 100
group_uri = lambda i: "%s/?idg=g%d&idt=%s" % (BASE, GROUP_FIRST + i, TH)

# groupes : dépendance logique (pilotes) puis une collection par liste scopée
GROUPS = [("SIAMOIS – Dépendance logique", "SIAMOIS – Logical dependency", DRIVERS)] + [(fr, en, ms) for fr, en, ms in COLLECTIONS]
for gi, (_, _, members) in enumerate(GROUPS):
    for m in members:
        m.groups.append(group_uri(gi))


def x(s):
    return escape(s, {'"': "&quot;"})


def attr(u):
    return x(u)


out = []
w = out.append
w('<?xml version="1.0" encoding="UTF-8"?>')
w('<rdf:RDF\n\txmlns:skos="http://www.w3.org/2004/02/skos/core#"\n\txmlns:dc="http://purl.org/dc/elements/1.1/"\n'
  '\txmlns:dcterms="http://purl.org/dc/terms/"\n\txmlns:geo="http://www.w3.org/2003/01/geo/wgs84_pos#"\n'
  '\txmlns:iso-thes="http://purl.org/iso25964/skos-thes#"\n\txmlns:opentheso="http://purl.org/umu/uneskos#"\n'
  '\txmlns:foaf="http://xmlns.com/foaf/0.1/"\n\txmlns:owl="http://www.w3.org/2002/07/owl#"\n'
  '\txmlns:wdt="http://www.opengis.net/ont/geosparql#"\n\txmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">\n')
w('<rdf:Description rdf:about="%s">' % SCHEME)
w('\t<rdf:type rdf:resource="http://www.w3.org/2004/02/skos/core#ConceptScheme"/>')
w('\t<skos:prefLabel xml:lang="fr">Siamois - Socle commun</skos:prefLabel>')
w('\t<skos:prefLabel xml:lang="en">Siamois - Common core</skos:prefLabel>')
w('\t<skos:hasTopConcept rdf:resource="%s"/>' % attr(URI["root"]))
w('\t<dcterms:creator>%s</dcterms:creator>\n\t<dcterms:contributor>%s</dcterms:contributor>' % (CREATOR, CREATOR))
w('\t<dcterms:title>Siamois - Socle commun</dcterms:title>')
w('\t<dcterms:description xml:lang="fr">Champs du socle commun SIAMOIS et listes de vocabulaire de démonstration, alignés sur le référentiel '
  'national relatif au rapport d\'opération d\'archéologie préventive.</dcterms:description>')
w('\t<dcterms:language>fr</dcterms:language>')
w('\t<dcterms:modified rdf:datatype="http://www.w3.org/2001/XMLSchema#date">%s</dcterms:modified>' % TODAY)
w('</rdf:Description>\n')

for gi, (fr, en, members) in enumerate(GROUPS):
    w('<rdf:Description rdf:about="%s">' % attr(group_uri(gi)))
    w('\t<rdf:type rdf:resource="http://www.w3.org/2004/02/skos/core#Collection"/>')
    w('\t<skos:prefLabel xml:lang="fr">%s</skos:prefLabel>' % x(fr))
    w('\t<skos:prefLabel xml:lang="en">%s</skos:prefLabel>' % x(en))
    w('\t<iso-thes:microThesaurusOf rdf:resource="%s"/>' % attr(group_uri(gi)))
    for m in members:
        w('\t<skos:member rdf:resource="%s"/>' % attr(URI[m.key]))
    w('\t<dcterms:created rdf:datatype="http://www.w3.org/2001/XMLSchema#date">%s</dcterms:created>' % TODAY)
    w('\t<dcterms:identifier>g%d</dcterms:identifier>' % (GROUP_FIRST + gi))
    w('</rdf:Description>\n')

for n in ORDER:
    w('<rdf:Description rdf:about="%s">' % attr(URI[n.key]))
    w('\t<rdf:type rdf:resource="http://www.w3.org/2004/02/skos/core#Concept"/>')
    w('\t<skos:prefLabel xml:lang="fr">%s</skos:prefLabel>' % x(n.fr))
    if n.en:
        w('\t<skos:prefLabel xml:lang="en">%s</skos:prefLabel>' % x(n.en))
    for lang, lab in n.labels_extra.items():
        w('\t<skos:prefLabel xml:lang="%s">%s</skos:prefLabel>' % (lang, x(lab)))
    for a in n.alt:
        w('\t<skos:altLabel xml:lang="fr">%s</skos:altLabel>' % x(a))
    for r in n.related:
        w('\t<skos:related rdf:resource="%s"/>' % attr(URI[r.key]))
    for m in n.matches:
        w('\t<skos:exactMatch rdf:resource="%s"/>' % attr(m))
    if n.parent is not None:
        w('\t<skos:broader rdf:resource="%s"/>' % attr(URI[n.parent.key]))
    for c in n.children:
        w('\t<skos:narrower rdf:resource="%s"/>' % attr(URI[c.key]))
    w('\t<skos:inScheme rdf:resource="%s"/>' % SCHEME)
    if n is ROOT:
        w('\t<skos:topConceptOf rdf:resource="%s"/>' % SCHEME)
    for g in n.groups:
        w('\t<opentheso:memberOf rdf:resource="%s"/>' % attr(g))
    if n.notation:
        w('\t<skos:notation>%s</skos:notation>' % x(n.notation))
    w('\t<dcterms:created rdf:datatype="http://www.w3.org/2001/XMLSchema#date">%s</dcterms:created>' % TODAY)
    w('\t<dcterms:modified rdf:datatype="http://www.w3.org/2001/XMLSchema#date">%s</dcterms:modified>' % TODAY)
    w('\t<dcterms:identifier>%d</dcterms:identifier>' % ids[n.key])
    w('\t<dcterms:creator>%s</dcterms:creator>' % CREATOR)
    for lang, d in n.defs.items():
        w('\t<skos:definition xml:lang="%s">%s</skos:definition>' % (lang, x(d)))
    w('</rdf:Description>\n')
w('</rdf:RDF>')

with open(args.out, "w", encoding="utf-8") as f:
    f.write("\n".join(out) + "\n")

# ================================================================================== RAPPORT
if ERRORS:
    print("ERREURS :")
    for e in ERRORS:
        print("  -", e)
n_fields = sum(1 for n in ORDER if getattr(n, "kind", None))
n_vocab = sum(1 for n in ORDER if getattr(n, "code", None) and getattr(n, "kind", None) in ("ONE", "MANY", "RELATIONS"))
print("concepts : %d (tables %d, champs %d dont %d à vocabulaire), collections : %d" %
      (len(ORDER), sum(1 for n in ORDER if n.parent is ROOT), n_fields, n_vocab, len(GROUPS)))
print("écrit :", args.out)
sys.exit(1 if ERRORS else 0)
