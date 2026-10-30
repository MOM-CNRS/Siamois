"""Génère le schéma ERD (SVG) : modèle de Quentin Verriez + tables techniques SIAMOIS du plugin."""
# Régénération : python3 build_erd_v2.py out.svg  puis capture avec Chromium (--headless --screenshot) ou ouverture du SVG.

import html
import sys

CHAR_W = 7.25          # largeur d'un caractère (DejaVu Sans Mono 12 px)
ROW_H = 18
HEAD_H = 28
PAD = 10
MARK_W = 30

BLUE = dict(head="#1d4ed8", body="#eff6ff", line="#93b4f5", text="#0b2a6f")
LBLUE = dict(head="#5b8def", body="#f4f8ff", line="#b9ceF8", text="#16346b")
GREEN = dict(head="#15803d", body="#f0fdf4", line="#86d19b", text="#0b3d1d")
PURPLE = dict(head="#6d28d9", body="#f5f3ff", line="#c4b5fd", text="#3b1a7a")
ORANGE = dict(head="#c2410c", body="#fff7ed", line="#f3b27c", text="#5a1d05")


class Box:
    def __init__(self, name, style, rows, subtitle=None, min_w=0):
        self.name, self.style, self.rows, self.subtitle = name, style, rows, subtitle
        longest = max([len(r[1]) for r in rows] + [len(name) + 2, len(subtitle or "") * 0.9])
        self.w = max(min_w, int(MARK_W + longest * CHAR_W + 2 * PAD))
        self.h = HEAD_H + (16 if subtitle else 0) + len(rows) * ROW_H + 8
        self.x = self.y = 0

    def place(self, x, y):
        self.x, self.y = x, y
        return self

    # points d'ancrage
    def top(self, dx=0.5): return (self.x + self.w * dx, self.y)
    def bottom(self, dx=0.5): return (self.x + self.w * dx, self.y + self.h)
    def left(self, dy=0.5): return (self.x, self.y + self.h * dy)
    def right(self, dy=0.5): return (self.x + self.w, self.y + self.h * dy)

    def svg(self):
        s, o = self.style, []
        o.append(f'<g filter="url(#shadow)"><rect x="{self.x}" y="{self.y}" width="{self.w}" height="{self.h}" rx="7" fill="{s["body"]}" stroke="{s["line"]}" stroke-width="1.5"/></g>')
        o.append(f'<path d="M{self.x},{self.y+28} V{self.y+7} Q{self.x},{self.y} {self.x+7},{self.y} H{self.x+self.w-7} Q{self.x+self.w},{self.y} {self.x+self.w},{self.y+7} V{self.y+28} Z" fill="{s["head"]}"/>')
        o.append(f'<text x="{self.x+PAD}" y="{self.y+19}" class="title">{html.escape(self.name)}</text>')
        y = self.y + HEAD_H
        if self.subtitle:
            o.append(f'<text x="{self.x+PAD}" y="{y+12}" class="sub" fill="{s["text"]}">{html.escape(self.subtitle)}</text>')
            y += 16
        for mark, text in self.rows:
            ty = y + 13
            if mark in ("SI", "NEW"):
                o.append(f'<rect x="{self.x+3}" y="{y+1}" width="{self.w-6}" height="{ROW_H-1}" rx="3" fill="#ffedd5"/>')
            if mark:
                col = {"PK": "#b45309", "FK": "#6d28d9", "UQ": "#0e7490", "SI": "#c2410c", "NEW": "#6d28d9"}.get(mark, "#475569")
                o.append(f'<text x="{self.x+PAD}" y="{ty}" class="mark" fill="{col}">{mark}</text>')
            cls = "col"
            fill = "#9a3412" if mark == "SI" else ("#4c1d95" if mark == "NEW" else s["text"])
            if text.startswith("+") or text.startswith("…"):
                cls, fill = "colmuted", "#64748b"
            o.append(f'<text x="{self.x+PAD+MARK_W-4}" y="{ty}" class="{cls}" fill="{fill}">{html.escape(text)}</text>')
            y += ROW_H
        return "\n".join(o)


edges = []


def pill(x, y, text, color="#475569", rot=None):
    w = len(text) * 6.4 + 12
    g = (f'<rect x="{x-w/2}" y="{y-9}" width="{w}" height="17" rx="8.5" fill="white" stroke="{color}" stroke-width="1"/>'
         f'<text x="{x}" y="{y+4}" text-anchor="middle" class="pill" fill="{color}">{html.escape(text)}</text>')
    return f'<g transform="rotate({rot} {x} {y})">{g}</g>' if rot else g


def curve(p, q, dp, dq, label=None, color="#64748b", dash=None, c1=None, c2=None, width=1.6, endcap=None, labelpos=0.5,
          startlabel=None, endlabel=None, dots=False):
    """Courbe de Bézier de p à q ; dp/dq = direction de sortie (vecteur unitaire) en p et q."""
    k = 70
    c1 = c1 or (p[0] + dp[0] * k, p[1] + dp[1] * k)
    c2 = c2 or (q[0] + dq[0] * k, q[1] + dq[1] * k)
    d = f"M{p[0]},{p[1]} C{c1[0]},{c1[1]} {c2[0]},{c2[1]} {q[0]},{q[1]}"
    dash_attr = f' stroke-dasharray="{dash}"' if dash else ""
    out = [f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{width}"{dash_attr} {" marker-start=\"url(#dot)\"" if dots else ""} marker-end="url(#{"dot" if dots else (endcap or "arrow")})"/>']
    t = labelpos
    mx = (1-t)**3*p[0] + 3*(1-t)**2*t*c1[0] + 3*(1-t)*t**2*c2[0] + t**3*q[0]
    my = (1-t)**3*p[1] + 3*(1-t)**2*t*c1[1] + 3*(1-t)*t**2*c2[1] + t**3*q[1]
    lab = pill(mx, my, label, color) if label else ""
    sl = f'<text x="{p[0]+dp[0]*14-4}" y="{p[1]+dp[1]*14+4}" class="card">{startlabel}</text>' if startlabel else ""
    el = f'<text x="{q[0]+dq[0]*14-4}" y="{q[1]+dq[1]*14+4}" class="card">{endlabel}</text>' if endlabel else ""
    return "\n".join(out) + lab + sl + el


UP, DOWN, LEFT, RIGHT = (0, -1), (0, 1), (-1, 0), (1, 0)
COMMON = "+ champs communs ¹"

# ======================================================================= tables du modèle de Quentin (+ colonnes SIAMOIS ajoutées)
SI_ID = [("SI", "siamois_id text"), ("SI", "siamois_revision integer")]

projet = Box("projet", BLUE, [
    ("PK", "idprojet integer"), ("FK", "idprojet_parent integer (NULL = racine)"), ("", "numoa text"),
    ("", "numprojet text *"), ("", "typprojet text *"), ("", "ro text * (personnes)"),
    ("", "geom polygone"), ("", COMMON), ("SI", "siamois_id text")], "Projet SIAMOIS + emprise")
metadonnees = Box("metadonnees", BLUE, [
    ("PK", "idmeta integer"), ("UQ", "idprojet integer → projet (1-1)"), ("", "dc_title, dc_creator, dc_subject"),
    ("", "dc_descri, dc_publish, dc_date"), ("", "dc_type, dc_format, dc_id, dc_source"), ("", "dc_lang, dc_relation"),
    ("", "dc_coverage, dc_right"), ("", "epsg integer *"), ("", "alti text *, geoid text *"), ("", COMMON),
    ("SI", "siamois_url text"), ("SI", "siamois_organisation_id text"), ("SI", "siamois_derniere_synchro datetime"),
    ("SI", "siamois_langue text")], "Dublin Core + référence spatiale + connexion")
concept = Box("concept", BLUE, [
    ("PK", "idconcept integer"), ("UQ", "nom_table text *"), ("UQ", "type_entite text"), ("UQ", "nom_colonne text *"),
    ("UQ", "label text *"), ("", "uri text"), ("", "autsaisie, datesaisie"),
    ("SI", "siamois_id_concept text"), ("SI", "siamois_code_vocabulaire text")],
    "Vocabulaire : terme ↔ URI (généré depuis SIAMOIS)")
personne = Box("personne", BLUE, [
    ("PK", "idpersonne integer"), ("UQ", "nom text * (Prénom Nom)"), ("", "typpersonne text *"),
    ("", "uri text (ORCID, IdRef, ROR)"), ("", "autsaisie, datesaisie"),
    ("SI", "siamois_id_personne text"), ("SI", "siamois_email text")], "Personnes et organismes")


def entity(name, pk, num, typ, geom, extra=(), subtitle=None):
    rows = [("PK", f"{pk} integer"), ("FK", "idprojet integer * → projet"), ("", f"{num} text *"), ("", f"{typ} text *")]
    rows += [("", e) for e in extra]
    if geom:
        rows.append(("", f"geom {geom}"))
    rows.append(("", COMMON))
    rows += SI_ID
    return Box(name, BLUE, rows, subtitle)


ut_poly = entity("ut_poly", "idutpoly", "numutpoly", "typutpoly", "polygone", ["metacquis text"], "UT — emprise, décapage…")
ut_ligne = entity("ut_ligne", "idutlgn", "numutlgn", "typutlgn", "ligne", ["metacquis text"], "UT — axes de coupe")
ut_point = entity("ut_point", "idutpt", "numutpt", "typutpt", "point", ["metacquis text"], "UT — cibles, altimétrie")
raster = entity("raster", "iddoc", "numdoc", "typdoc", "polygone", ["lien text (URL/chemin)"], "Emprise d'un raster")
uo_poly = entity("uo_poly", "iduopoly", "numuopoly", "typuopoly", "polygone",
                 ["interpret text", "datedebut, datefin integer", "metacquis text"], "UO — US, Faits…")
uo_point = entity("uo_point", "iduopt", "numuopt", "typuopt", "point",
                  ["interpret text", "datedebut, datefin integer", "metacquis text"], "UO — isolats, prélèvements")
ur_poly = entity("ur_poly", "idurpoly", "numurpoly", "typurpoly", "polygone",
                 ["interpret text", "datedebut, datefin integer", "metacquis text"], "UR — restitutions")
ud_poly = entity("ud_poly", "idudpoly", "numudpoly", "typudpoly", "polygone", [], "UD — détails surfaciques")
ud_ligne = entity("ud_ligne", "idudlgn", "numudlgn", "typudlgn", "ligne", [], "UD — détails linéaires")

lien = Box("lien_* (7 tables)", LBLUE, [
    ("PK", "idlien integer"), ("FK", "clé étrangère A integer"), ("FK", "clé étrangère B integer"),
    ("UQ", "UNIQUE (A, B)"), ("", "autsaisie, datesaisie")], "Tables de liaison n-n", min_w=290)

# ======================================================================= tables techniques conservées
champ = Box("siamois_champ", GREEN, [
    ("PK", "idchamp integer"), ("", "nom_table text (NULL = toutes)"), ("", "nom_colonne text"),
    ("", "colonne_modele boolean (colonne de Quentin)"), ("", "id_champ_siamois text"), ("", "binding text (num, type, tpq…)"),
    ("", "libelle text"), ("", "type_reponse text"), ("", "code_vocabulaire text → concept.siamois_code_vocabulaire"),
    ("", "panneau text, ordre integer"), ("", "obligatoire, lecture_seule boolean"),
    ("", "borne_min, borne_max, unite"), ("", "regles_json text")],
    "ex-« layers.columns » : config des champs + mapping")
champ_type = Box("siamois_champ_type", GREEN, [
    ("PK", "id integer"), ("", "nom_table text"), ("", "id_type_siamois text (concept)"),
    ("FK", "idchamp → siamois_champ")], "ex-« allowed » : champs d'un type")
reference = Box("siamois_reference", GREEN, [
    ("PK", "nom_table + id_local"), ("", "cellules_json text"), ("", "geom_wkt text"), ("", "revision integer"),
    ("", "incomplet_json text"), ("", "champs_autorises_json text")], "ex-« base » : copie de référence (hors ligne)")
journal = Box("siamois_journal", ORANGE, [
    ("PK", "idjournal integer"), ("", "date datetime, utilisateur text"), ("", "action text (pull|push|create)"),
    ("", "nom_table text, id_local integer"), ("", "siamois_id text"), ("", "statut text (ok|erreur|conflit)"),
    ("", "message text"), ("", "detail_json text (conflits…)")], "historique + détail des conflits")

# ======================================================================= côté serveur SIAMOIS (à développer)
srv = Box("Configuration d'un type — SIAMOIS", PURPLE, [
    ("PK", "FormConfig.id"), ("", "projet (action unit)"), ("", "table configurable (UE|Mobilier|Doc)"),
    ("", "concept du type"), ("", "formulaire (layout) + champs"), ("", "identifierConfig"),
    ("NEW", "table_modele_spatial (nouveau)"), ("", "  ut_poly | ut_ligne | ut_point"), ("", "  uo_poly | uo_point | ur_poly"),
    ("", "  ud_poly | ud_ligne | aucune")], "côté serveur · exposé par l'API (…-types)", min_w=400)

# ======================================================================= placement
M = 60
col_w = 280
gap = 150
vgap = 96

projet.place(M, 100)
metadonnees.place(M, projet.y + projet.h + 50)

fx = M + max(projet.w, metadonnees.w) + 170
grid_x = [fx + 28, fx + 28 + col_w + gap, fx + 28 + 2 * (col_w + gap)]
rows = [[uo_point, raster, lien], [ud_poly, uo_poly, ud_ligne], [None, ur_poly, None], [ut_poly, ut_ligne, ut_point]]
y = 142
row_y = []
for r in rows:
    row_y.append(y)
    for bx_, b_ in zip(grid_x, r):
        if b_ is None:
            continue
        b_.w = max(col_w, b_.w)
        b_.place(bx_, y)
    y += max(b_.h for b_ in r if b_ is not None) + vgap
frame = (fx, 96, grid_x[2] + col_w + 28 - fx, y - vgap - 96 + 34)
frame_bottom = frame[1] + frame[3]
rx = frame[0] + frame[2] + 175

# bande basse
cols = [[champ_type, personne], [champ, None], [reference, None], [journal, None]]
bx = []
xx = M
for c in cols:
    bx.append(xx)
    xx += max(b_.w for b_ in c if b_ is not None) + 84
by1 = frame_bottom + 70
by2 = by1 + max(b_.h for b_ in (champ_type, champ, reference, journal)) + 70
champ_type.place(bx[0], by1)
champ.place(bx[1], by1)
reference.place(bx[2], by1)
journal.place(bx[3], by1)
personne.place(bx[0], by2)
srv.place(rx, 100)
concept.w = 440
concept.place(rx, srv.y + srv.h + 64)
W = rx + 440 + 40

# ======================================================================= arêtes
E = []
E.append(curve(projet.right(0.35), (frame[0], projet.y + projet.h * 0.35), RIGHT, LEFT, "idprojet ×9", "#1d4ed8",
               width=2.2, startlabel="1", endlabel="n", c1=(projet.right()[0] + 40, projet.y + projet.h * 0.35),
               c2=(frame[0] - 40, projet.y + projet.h * 0.35)))
px, py = projet.right(0.62)
E.append(f'<path d="M{px},{py} C{px+70},{py-30} {px+70},{py+30} {px},{py+16}" fill="none" stroke="#1d4ed8" stroke-width="1.6" marker-end="url(#arrow)"/>'
         + pill(px + 80, py + 8, "parent", "#1d4ed8"))
E.append(curve(metadonnees.top(0.5), projet.bottom(0.5), UP, DOWN, "1 — 1", "#1d4ed8", c1=(metadonnees.top()[0], metadonnees.y - 26),
               c2=(projet.bottom()[0], projet.y + projet.h + 26)))
LC = "#5b8def"
def horiz(l, r, label, dy=0.5):
    return curve(l.right(dy), r.left(dy), RIGHT, LEFT, label, LC, width=1.5, dots=True,
                 c1=(l.right()[0] + 10, l.right(dy)[1]), c2=(r.left()[0] - 10, r.left(dy)[1]))
def vert(t, u, label, dx=0.5):
    return curve(t.bottom(dx), u.top(dx), DOWN, UP, label, LC, width=1.5, dots=True,
                 c1=(t.bottom(dx)[0], t.bottom()[1] + 12), c2=(u.top(dx)[0], u.y - 12))
E.append(horiz(uo_point, raster, "lien_doc_uopt"))
E.append(vert(raster, uo_poly, "lien_doc_uopoly"))
E.append(horiz(ud_poly, uo_poly, "lien_uopoly_udpoly"))
E.append(horiz(uo_poly, ud_ligne, "lien_uopoly_udlgn"))
E.append(vert(uo_poly, ur_poly, "lien_uopoly_urpoly"))
p, q = ud_poly.bottom(0.5), ur_poly.left(0.45)
E.append(curve(p, q, DOWN, LEFT, "lien_urpoly_udpoly", LC, width=1.5, dots=True, c1=(p[0], p[1] + 70), c2=(q[0] - 70, q[1]), labelpos=0.55))
p, q = ud_ligne.bottom(0.5), ur_poly.right(0.45)
E.append(curve(p, q, DOWN, RIGHT, "lien_urpoly_udlgn", LC, width=1.5, dots=True, c1=(p[0], p[1] + 70), c2=(q[0] + 70, q[1]), labelpos=0.55))

fbot = frame_bottom
def to_frame(box, label, color, dx=0.5):
    p = box.top(dx)
    q = (p[0], fbot)
    return curve(p, q, UP, DOWN, label, color, dash="6 4", c1=(p[0], p[1] - 24), c2=(q[0], q[1] + 24))
E.append(to_frame(champ, "(nom_table, nom_colonne)", GREEN["head"]))
E.append(to_frame(reference, "(nom_table, id_local)", GREEN["head"]))
E.append(to_frame(journal, "(nom_table, id_local)", ORANGE["head"]))
E.append(curve(champ_type.right(0.5), champ.left(0.5), RIGHT, LEFT, "n — 1", GREEN["head"],
               c1=(champ_type.right()[0] + 14, champ_type.right(0.5)[1]), c2=(champ.left()[0] - 14, champ.left(0.5)[1])))
# serveur → cadre des entités (répartition) et → concept (génération)
p = srv.left(0.5)
q = (frame[0] + frame[2], p[1])
E.append(curve(p, q, LEFT, RIGHT, "table_modele_spatial", PURPLE["head"], width=2.2, dash="7 4",
               c1=(p[0] - 30, p[1]), c2=(q[0] + 30, q[1])))
# serveur → concept : flèche courte (le référentiel est généré depuis la config des types)
E.append(curve(srv.bottom(0.5), concept.top(0.5), DOWN, UP, "génère concept (nom_table, label, uri)", PURPLE["head"], dash="7 4", width=2.2,
               c1=(srv.bottom()[0], srv.y + srv.h + 20), c2=(concept.top()[0], concept.y - 20)))

# ======================================================================= notes
def note(x, y, w, title, lines, fill="#f8fafc", stroke="#cbd5e1", title_color="#0f172a"):
    h = 34 + len(lines) * 17
    o = [f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="9" fill="{fill}" stroke="{stroke}" stroke-width="1.4"/>',
         f'<text x="{x+12}" y="{y+22}" class="ntitle" fill="{title_color}">{html.escape(title)}</text>']
    for i, ln in enumerate(lines):
        o.append(f'<text x="{x+12}" y="{y+42+i*17}" class="ntext">{html.escape(ln)}</text>')
    return "\n".join(o), h

RW = 440
ny = concept.y + concept.h + 24
parts = []
def add_note(title, lines, **kw):
    global ny
    t, h = note(rx, ny, RW, title, lines, **kw)
    parts.append(t)
    ny += h + 20

add_note("Décisions", [
    "• Colonnes siamois_* ajoutées aux tables de Quentin",
    "  (orange) : id SIAMOIS, révision, connexion…",
    "• Répartition d'une UE dans la bonne table :",
    "  type SIAMOIS → table_modele_spatial (config",
    "  du type) ; non renseigné = « non classé », non",
    "  chargé. La géométrie se déduit de la table.",
    "• Le référentiel concept est GÉNÉRÉ depuis cette",
    "  config : les menus de types sont filtrés par table.",
    "• Supprimées : siamois_entite, siamois_concept,",
    "  siamois_personne, siamois_table, siamois_conflit,",
    "  siamois_connexion (→ colonnes ou code du plugin).",
    "• L'état (nouveau / modifié) se déduit : pas de",
    "  siamois_id = nouveau ; ≠ copie de référence = modifié.",
], fill="#fff7ed", stroke="#f3b27c")
add_note("Colonnes de Quentin ↔ champs SIAMOIS (pas de doublon)", [
    "num…         ↔ identifiant de l'UE (généré par le serveur)",
    "typ…         ↔ type (concept)  → table_modele_spatial",
    "datedebut/fin ↔ TPQ / TAQ",
    "interpret    ↔ interprétation",
    "com          ↔ commentaires",
    "autsaisie, ro ↔ auteur / responsable (personne.nom)",
    "Seuls les champs additionnels du projet SIAMOIS",
    "s'ajoutent à droite ; le mapping est dans siamois_champ.",
], fill="#f5f3ff", stroke="#c4b5fd")
add_note("Correspondance SIAMOIS (champ table_siamois)", [
    "projet               → Projet",
    "ut_*, uo_poly,       → Unité d'enregistrement",
    "ur_poly, ud_*",
    "uo_point             → Mobilier",
    "                       (API : UE parente + géométrie)",
    "raster, metadonnees  → Document",
    "¹ champs communs : table_siamois, com,",
    "  autsaisie, datesaisie",
])
legend_items = [
    (BLUE["head"], "Modèle de Quentin Verriez"),
    ("#ffedd5", "Colonne SIAMOIS ajoutée à une table du modèle"),
    (GREEN["head"], "Table technique conservée (déjà dans le plugin)"),
    (ORANGE["head"], "Table technique nouvelle"),
    (PURPLE["head"], "Côté serveur SIAMOIS (à développer)"),
]
lh = 34 + len(legend_items) * 24 + 70
leg = [f'<rect x="{rx}" y="{ny}" width="{RW}" height="{lh}" rx="9" fill="#ffffff" stroke="#cbd5e1" stroke-width="1.4"/>',
       f'<text x="{rx+12}" y="{ny+22}" class="ntitle">Légende</text>']
for i, (c, t) in enumerate(legend_items):
    yy = ny + 42 + i * 24
    leg.append(f'<rect x="{rx+12}" y="{yy-11}" width="16" height="16" rx="4" fill="{c}" stroke="#c2410c" stroke-width="{1 if c == "#ffedd5" else 0}"/>')
    leg.append(f'<text x="{rx+36}" y="{yy+2}" class="ntext">{html.escape(t)}</text>')
yy = ny + 42 + len(legend_items) * 24 + 4
leg.append(f'<text x="{rx+12}" y="{yy+4}" class="ntext">PK clé primaire · FK clé étrangère · UQ unique</text>')
leg.append(f'<text x="{rx+12}" y="{yy+22}" class="ntext">* obligatoire · tirets : lien logique ou génération</text>')
parts += leg
ny += lh + 20
add_note("Travail hors ligne", [
    "Les modifications restent dans le GeoPackage.",
    "siamois_reference garde l'état du dernier chargement :",
    "la différence avec les tables = ce qu'il y a à envoyer.",
    "Un seul bouton « Synchroniser » ; tout est tracé dans",
    "siamois_journal (conflits 409 compris).",
], fill="#f0fdf4", stroke="#86d19b")
notes_svg = "\n".join(parts)
H = max(by2 + max(b_.h for b_ in (concept, personne)) + 50, ny + 30)

# ======================================================================= assemblage
boxes = [projet, metadonnees, concept, personne, ut_poly, ut_ligne, ut_point, raster, uo_poly, uo_point, ur_poly,
         ud_poly, ud_ligne, lien, champ, champ_type, reference, journal, srv]

svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">',
       '''<defs>
<filter id="shadow" x="-5%" y="-5%" width="110%" height="115%"><feDropShadow dx="0" dy="1.5" stdDeviation="2.2" flood-color="#0f172a" flood-opacity="0.18"/></filter>
<marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M0,1 L9,5 L0,9 z" fill="#475569"/></marker>
<marker id="dot" viewBox="0 0 10 10" refX="5" refY="5" markerWidth="6" markerHeight="6"><circle cx="5" cy="5" r="4" fill="#5b8def"/></marker>
<marker id="arrow2" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,1 L9,5 L0,9 z" fill="#5b8def"/></marker>
<style>
text { font-family: "DejaVu Sans Mono", Menlo, Consolas, monospace; }
.title { font-size: 14px; font-weight: 700; fill: #ffffff; }
.sub { font-size: 10.5px; font-style: italic; }
.col { font-size: 12px; } .colmuted { font-size: 11px; }
.mark { font-size: 10px; font-weight: 700; }
.pill { font-size: 10px; } .card { font-size: 11px; font-weight: 700; fill: #334155; }
.ntitle { font-size: 13px; font-weight: 700; } .ntext { font-size: 11.5px; fill: #1e293b; }
.big { font-family: "DejaVu Sans", Helvetica, Arial, sans-serif; font-size: 24px; font-weight: 700; fill: #0f172a; }
.small { font-family: "DejaVu Sans", Helvetica, Arial, sans-serif; font-size: 13px; fill: #475569; }
.frame { font-family: "DejaVu Sans", Helvetica, Arial, sans-serif; font-size: 14px; font-weight: 700; fill: #1d4ed8; }
</style></defs>''',
       f'<rect width="{W}" height="{H}" fill="#ffffff"/>',
       f'<text x="{M}" y="40" class="big">Modèle de Quentin Verriez × SIAMOIS — version simplifiée (décisions intégrées)</text>',
       f'<text x="{M}" y="62" class="small">Colonnes siamois_* ajoutées aux tables du modèle (orange), 4 tables techniques conservées, et un champ table_modele_spatial à ajouter à la configuration des types côté SIAMOIS.</text>',
       f'<rect x="{frame[0]}" y="{frame[1]}" width="{frame[2]}" height="{frame[3]}" rx="14" fill="#f8fbff" stroke="#93b4f5" stroke-width="1.6" stroke-dasharray="8 5"/>',
       f'<text x="{frame[0]+16}" y="{frame[1]+22}" class="frame">Entités spatiales (9 tables) — chacune rattachée à un seul projet</text>',
       ]
svg += E
svg += [b.svg() for b in boxes]
svg += [notes_svg]
svg.append('</svg>')

out = sys.argv[1]
open(out, "w", encoding="utf-8").write("\n".join(svg))
print(out, int(W), int(H))
