"""Génère le schéma ERD (SVG) : modèle de Quentin Verriez + tables techniques SIAMOIS du plugin."""
# Régénération : python3 build_erd.py out.svg  puis capture avec Chromium (--headless --screenshot) ou ouverture du SVG.

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
            if mark:
                col = {"PK": "#b45309", "FK": "#6d28d9", "UQ": "#0e7490"}.get(mark, "#475569")
                o.append(f'<text x="{self.x+PAD}" y="{ty}" class="mark" fill="{col}">{mark}</text>')
            cls = "col"
            fill = s["text"]
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

# ======================================================================= tables du modèle de Quentin
projet = Box("projet", BLUE, [
    ("PK", "idprojet integer"), ("FK", "idprojet_parent integer (NULL = racine)"), ("", "numoa text"),
    ("", "numprojet text *"), ("", "typprojet text *"), ("", "ro text * (personnes)"),
    ("", "geom polygone"), ("", COMMON)], "Projet SIAMOIS + emprise")
metadonnees = Box("metadonnees", BLUE, [
    ("PK", "idmeta integer"), ("UQ", "idprojet integer → projet (1-1)"), ("", "dc_title, dc_creator, dc_subject"),
    ("", "dc_descri, dc_publish, dc_date"), ("", "dc_type, dc_format, dc_id, dc_source"), ("", "dc_lang, dc_relation"),
    ("", "dc_coverage, dc_right"), ("", "epsg integer *"), ("", "alti text *, geoid text *"),
    ("", "+ champs communs ¹")], "Dublin Core + référence spatiale")
concept = Box("concept", BLUE, [
    ("PK", "idconcept integer"), ("UQ", "nom_table text *"), ("UQ", "type_entite text"), ("UQ", "nom_colonne text *"),
    ("UQ", "label text *"), ("", "uri text"), ("", "autsaisie, datesaisie")], "Vocabulaire : terme ↔ URI")
personne = Box("personne", BLUE, [
    ("PK", "idpersonne integer"), ("UQ", "nom text * (Prénom Nom)"), ("", "typpersonne text * (personne|organisme)"),
    ("", "uri text (ORCID, IdRef, ROR)"), ("", "autsaisie, datesaisie")], "Personnes et organismes")


def entity(name, pk, num, typ, geom, extra=(), subtitle=None):
    rows = [("PK", f"{pk} integer"), ("FK", "idprojet integer * → projet"), ("", f"{num} text *"), ("", f"{typ} text *")]
    rows += [("", e) for e in extra]
    if geom:
        rows.append(("", f"geom {geom}"))
    rows.append(("", COMMON))
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

# ======================================================================= tables techniques (déjà dans le plugin = vert)
connexion = Box("siamois_connexion", GREEN, [
    ("PK", "id integer (1 ligne)"), ("", "base_url text"), ("", "organisation_id text"),
    ("FK", "idprojet_racine → projet"), ("", "langue text"), ("", "schema_version text"),
    ("", "derniere_synchro datetime")], "ex-« meta » : où est le serveur")
t_table = Box("siamois_table", ORANGE, [
    ("PK", "nom_table text"), ("", "entite_siamois text"), ("", "famille text (UT|UO|UR|UD)"),
    ("", "type_geometrie text"), ("", "champ_cle text"), ("", "champ_num text"), ("", "editable boolean")],
    "table locale → entité SIAMOIS")
champ = Box("siamois_champ", GREEN, [
    ("PK", "idchamp integer"), ("FK", "nom_table → siamois_table"), ("", "nom_colonne text"),
    ("", "id_champ_siamois text"), ("", "libelle text"), ("", "type_reponse text"), ("", "code_vocabulaire text"),
    ("", "binding text (type, recordingUnit…)"), ("", "panneau text, ordre integer"),
    ("", "obligatoire, lecture_seule boolean"), ("", "borne_min, borne_max, unite"), ("", "regles_json text")],
    "ex-« layers.columns » : config des champs")
champ_type = Box("siamois_champ_type", GREEN, [
    ("PK", "id integer"), ("", "nom_table text"), ("", "id_type_siamois text"),
    ("FK", "idchamp → siamois_champ")], "ex-« allowed » : champs d'un type")
s_concept = Box("siamois_concept", GREEN, [
    ("PK", "id integer"), ("FK", "idconcept → concept"), ("", "id_concept_siamois text"),
    ("", "code_vocabulaire text (fieldCode)"), ("", "libelle_prefere text")], "ex-« vocab » : lien concept ↔ SIAMOIS")
s_personne = Box("siamois_personne", ORANGE, [
    ("PK", "id integer"), ("FK", "idpersonne → personne"), ("", "id_personne_siamois text"),
    ("", "email text")], "personne ↔ utilisateur SIAMOIS")
entite = Box("siamois_entite", ORANGE, [
    ("PK", "identite integer"), ("", "nom_table text"), ("", "id_local integer (clé de la table)"),
    ("", "id_siamois text"), ("", "type_siamois text (projet|UE|mobilier|doc)"),
    ("", "revision_siamois integer"), ("", "etat text (synchronise|modifie|"), ("", "      nouveau|conflit|erreur)"),
    ("", "date_synchro datetime")], "correspondance locale ↔ SIAMOIS + état")
reference = Box("siamois_reference", GREEN, [
    ("PK", "nom_table + id_local"), ("", "cellules_json text"), ("", "geom_wkt text"), ("", "revision integer"),
    ("", "incomplet_json text"), ("", "champs_autorises_json text")], "ex-« base » : copie de référence (hors ligne)")
conflit = Box("siamois_conflit", ORANGE, [
    ("PK", "idconflit integer"), ("FK", "identite → siamois_entite"), ("", "nom_colonne text"),
    ("", "valeur_locale text"), ("", "valeur_serveur text"), ("", "decision text (local|serveur|fusion)"),
    ("", "date datetime")], "conflits de révision (409)")
journal = Box("siamois_journal", ORANGE, [
    ("PK", "idjournal integer"), ("", "date datetime, utilisateur text"), ("", "action text (pull|push|create)"),
    ("FK", "identite → siamois_entite"), ("", "statut text (ok|erreur|conflit)"), ("", "message text")],
    "historique des synchronisations")

# ======================================================================= placement
M = 60
col_w = 270
gap = 150          # espace entre colonnes : y loger les pastilles des tables de liaison
vgap = 96

projet.place(M, 100)
metadonnees.place(M, projet.y + projet.h + 50)

fx = M + max(projet.w, metadonnees.w) + 170            # cadre des entités
grid_x = [fx + 28, fx + 28 + col_w + gap, fx + 28 + 2 * (col_w + gap)]
# grille : l'axe uo_poly / ur_poly au centre, ud_* de part et d'autre, raster au-dessus, UT en dessous
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
frame = (fx, 96, grid_x[2] + col_w + 28 - fx, y - vgap - 96 + 34)   # x, y, w, h
frame_bottom = frame[1] + frame[3]
rx = frame[0] + frame[2] + 40

# bande basse : colonnes alignées sur la largeur réelle des boîtes
cols3 = [[connexion, champ_type, concept], [t_table, champ, s_concept], [entite, conflit, personne], [reference, journal, s_personne]]
bx = []
xx = M
for c in cols3:
    bx.append(xx)
    xx += max(b_.w for b_ in c) + 84
by1 = frame_bottom + 70
by2 = by1 + max(b_.h for b_ in (connexion, t_table, entite, reference)) + 56
by3 = by2 + max(b_.h for b_ in (champ_type, champ, conflit, journal)) + 56
for c, x0 in zip(cols3, bx):
    for b_, yy_ in zip(c, (by1, by2, by3)):
        b_.place(x0, yy_)
W = rx + 400 + 40
H = by3 + max(b_.h for b_ in (concept, s_concept, personne, s_personne)) + 50

# ======================================================================= arêtes
E = []
# projet → cadre des entités (idprojet)
E.append(curve(projet.right(0.35), (frame[0], projet.y + projet.h * 0.35), RIGHT, LEFT, "idprojet ×9", "#1d4ed8",
               width=2.2, startlabel="1", endlabel="n", c1=(projet.right()[0] + 40, projet.y + projet.h * 0.35),
               c2=(frame[0] - 40, projet.y + projet.h * 0.35)))
# auto-référence projet
px, py = projet.right(0.72)
E.append(f'<path d="M{px},{py} C{px+70},{py-30} {px+70},{py+30} {px},{py+16}" fill="none" stroke="#1d4ed8" stroke-width="1.6" marker-end="url(#arrow)"/>'
         + pill(px + 80, py + 8, "parent", "#1d4ed8"))
# metadonnees → projet
E.append(curve(metadonnees.top(0.5), projet.bottom(0.5), UP, DOWN, "1 — 1", "#1d4ed8", c1=(metadonnees.top()[0], metadonnees.y - 26),
               c2=(projet.bottom()[0], projet.y + projet.h + 26)))
# liens n-n entre entités (pastilles = tables de liaison)
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
E.append(curve(p, q, DOWN, LEFT, "lien_urpoly_udpoly", LC, width=1.5, dots=True,
               c1=(p[0], p[1] + 70), c2=(q[0] - 70, q[1]), labelpos=0.55))
p, q = ud_ligne.bottom(0.5), ur_poly.right(0.45)
E.append(curve(p, q, DOWN, RIGHT, "lien_urpoly_udlgn", LC, width=1.5, dots=True,
               c1=(p[0], p[1] + 70), c2=(q[0] + 70, q[1]), labelpos=0.55))
# technique ↔ cadre des entités
fbot = frame_bottom
def to_frame(box, label, color, dx=0.5):
    p = box.top(dx)
    q = (p[0], fbot)
    return curve(p, q, UP, DOWN, label, color, dash="6 4", c1=(p[0], p[1] - 24), c2=(q[0], q[1] + 24), labelpos=0.5)

E.append(to_frame(t_table, "nom_table", ORANGE["head"]))
E.append(to_frame(entite, "(nom_table, id_local)", ORANGE["head"]))
E.append(to_frame(reference, "(nom_table, id_local)", GREEN["head"]))
# connexion → projet (par la marge gauche)
cx, cy = connexion.left(0.4)
tx, ty = projet.left(0.5)
E.append(f'<path d="M{cx},{cy} C{M-40},{cy} {M-40},{ty} {tx},{ty}" fill="none" stroke="{GREEN["head"]}" stroke-width="1.6" marker-end="url(#arrow)"/>'
         + pill(M - 31, (cy + ty) / 2, "projet racine", GREEN["head"], rot=-90))
# champ → table, champ_type → champ, conflit → entite, journal → entite
E.append(curve(champ.top(0.5), t_table.bottom(0.5), UP, DOWN, "n — 1", GREEN["head"], c1=(champ.top()[0], champ.y - 24), c2=(t_table.bottom()[0], t_table.y + t_table.h + 24)))
E.append(curve(champ_type.right(0.5), champ.left(0.5), RIGHT, LEFT, "n — 1", GREEN["head"], c1=(champ_type.right()[0] + 14, champ_type.right(0.5)[1]), c2=(champ.left()[0] - 14, champ.left(0.5)[1])))
E.append(curve(conflit.top(0.4), entite.bottom(0.4), UP, DOWN, "n — 1", ORANGE["head"], c1=(conflit.top(0.4)[0], conflit.y - 24), c2=(entite.bottom(0.4)[0], entite.y + entite.h + 24)))
E.append(curve(journal.top(0.2), entite.bottom(0.9), UP, DOWN, "n — 1", ORANGE["head"], c1=(journal.top(0.2)[0], journal.y - 30), c2=(entite.bottom(0.9)[0], entite.y + entite.h + 30), labelpos=0.5))
# concept / personne ↔ techniques
E.append(curve(s_concept.left(0.5), concept.right(0.5), LEFT, RIGHT, "1 — 1", GREEN["head"], c1=(s_concept.left()[0] - 14, s_concept.left(0.5)[1]), c2=(concept.right()[0] + 14, concept.right(0.5)[1])))
E.append(curve(s_personne.left(0.5), personne.right(0.5), LEFT, RIGHT, "1 — 1", ORANGE["head"], c1=(s_personne.left()[0] - 14, s_personne.left(0.5)[1]), c2=(personne.right()[0] + 14, personne.right(0.5)[1])))
# champ → vocabulaire (code_vocabulaire)
E.append(curve(champ.bottom(0.3), s_concept.top(0.3), DOWN, UP, "code_vocabulaire", GREEN["head"], dash="6 4", c1=(champ.bottom(0.3)[0], champ.y + champ.h + 26), c2=(s_concept.top(0.3)[0], s_concept.y - 26)))

# ======================================================================= notes, légende, correspondance
def note(x, y, w, title, lines, fill="#f8fafc", stroke="#cbd5e1", title_color="#0f172a"):
    h = 34 + len(lines) * 17
    o = [f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="9" fill="{fill}" stroke="{stroke}" stroke-width="1.4"/>',
         f'<text x="{x+12}" y="{y+22}" class="ntitle" fill="{title_color}">{html.escape(title)}</text>']
    for i, ln in enumerate(lines):
        o.append(f'<text x="{x+12}" y="{y+42+i*17}" class="ntext">{html.escape(ln)}</text>')
    return "\n".join(o), h

RW = 400
ny = 100
parts = []
def add_note(title, lines, **kw):
    global ny
    svgtxt, h = note(rx, ny, RW, title, lines, **kw)
    parts.append(svgtxt)
    ny += h + 22

add_note("Correspondance SIAMOIS (champ table_siamois)", [
    "projet               → Projet",
    "ut_*, uo_poly,       → Unité d'enregistrement",
    "ur_poly, ud_*",
    "uo_point             → Mobilier",
    "                       (API : UE parente + géométrie)",
    "raster, metadonnees  → Document",
    "personne ↔ Utilisateur · concept ↔ Concept",
    "¹ champs communs : table_siamois, com,",
    "  autsaisie, datesaisie",
])
add_note("Liens « par la valeur » (sans clé étrangère)", [
    "typ…, interpret, metacquis, table_siamois",
    "      →  concept.label",
    "autsaisie, ro, dc_creator, dc_publish",
    "      →  personne.nom",
    "(valeurs multiples séparées par des virgules)",
], fill="#fffbeb", stroke="#f5d68a")

legend_items = [
    (BLUE["head"], "Modèle de Quentin Verriez"),
    (LBLUE["head"], "Tables de liaison du modèle (n-n)"),
    (GREEN["head"], "Tables techniques déjà dans le plugin"),
    (ORANGE["head"], "Tables techniques à ajouter"),
]
lh = 34 + len(legend_items) * 24 + 70
leg = [f'<rect x="{rx}" y="{ny}" width="{RW}" height="{lh}" rx="9" fill="#ffffff" stroke="#cbd5e1" stroke-width="1.4"/>',
       f'<text x="{rx+12}" y="{ny+22}" class="ntitle">Légende</text>']
for i, (c, t) in enumerate(legend_items):
    yy = ny + 42 + i * 24
    leg.append(f'<rect x="{rx+12}" y="{yy-11}" width="16" height="16" rx="4" fill="{c}"/>')
    leg.append(f'<text x="{rx+36}" y="{yy+2}" class="ntext">{html.escape(t)}</text>')
yy = ny + 42 + len(legend_items) * 24 + 4
leg.append(f'<text x="{rx+12}" y="{yy+4}" class="ntext">PK clé primaire · FK clé étrangère · UQ unique</text>')
leg.append(f'<text x="{rx+12}" y="{yy+22}" class="ntext">* obligatoire · trait plein : clé étrangère</text>')
leg.append(f'<text x="{rx+12}" y="{yy+40}" class="ntext">tirets : lien logique (nom_table, id_local)</text>')
parts += leg
ny += lh + 22

add_note("Travail hors ligne et synchronisation", [
    "Les modifications restent dans le GeoPackage.",
    "siamois_reference garde l'état du dernier",
    "chargement : la différence avec les tables",
    "= ce qu'il y a à envoyer.",
    "siamois_entite.etat suit chaque ligne :",
    "synchronise · modifie · nouveau · conflit · erreur.",
    "Un seul bouton « Synchroniser » ; pas de file",
    "d'attente. Les conflits (409) vont dans",
    "siamois_conflit, tout est tracé dans le journal.",
], fill="#f0fdf4", stroke="#86d19b")
note1 = "\n".join(parts)
note2 = note3 = ""
leg = []

# ======================================================================= assemblage
boxes = [projet, metadonnees, concept, personne, ut_poly, ut_ligne, ut_point, raster, uo_poly, uo_point, ur_poly,
         ud_poly, ud_ligne, lien, connexion, t_table, champ, champ_type, s_concept, s_personne, entite, reference,
         conflit, journal]

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
       f'<text x="{M}" y="40" class="big">Schéma de données spatiales SIAMOIS — modèle de Quentin Verriez + tables techniques du plugin QGIS</text>',
       f'<text x="{M}" y="62" class="small">GeoPackage d\'échange : 13 tables d\'entités et de référence, 7 tables de liaison, et les tables siamois_* qui portent la configuration, les vocabulaires, la synchronisation et le travail hors ligne.</text>',
       f'<rect x="{frame[0]}" y="{frame[1]}" width="{frame[2]}" height="{frame[3]}" rx="14" fill="#f8fbff" stroke="#93b4f5" stroke-width="1.6" stroke-dasharray="8 5"/>',
       f'<text x="{frame[0]+16}" y="{frame[1]+22}" class="frame">Entités spatiales (9 tables) — chacune rattachée à un seul projet</text>',
       ]
svg += E
svg += [b.svg() for b in boxes]
svg += [note1]
svg.append('</svg>')

out = sys.argv[1]
open(out, "w", encoding="utf-8").write("\n".join(svg))
print(out, int(W), int(H))
