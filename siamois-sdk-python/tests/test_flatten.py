import unittest

from siamois_sdk import parse_form
from siamois_sdk.flatten import (
    Vocabulary, cells_to_patch, flatten_schema, merge_schemas, normalize, row_to_cells,
)

LAYOUT = [{"name": "G", "rows": [{"columns": [
    {"fieldId": 1, "width": {"span": 6}, "isRequired": True},
    {"fieldId": 2, "width": {"span": 6}},
    {"fieldId": 3, "width": {"span": 6}},
    {"fieldId": 4, "width": {"span": 6}},
    {"fieldId": 5, "width": {"span": 6}},
    {"fieldId": 6, "width": {"span": 6}},
    {"fieldId": 7, "width": {"span": 6}},
    {"fieldId": 8, "width": {"span": 6}, "isReadOnly": True},
]}]}]
F = lambda i, label, t, **kw: {"id": str(i), "label": label, "answerType": t, **kw}
FIELDS = {
    "1": F(1, "Identifiant", "TEXT"),
    "2": F(2, "Type", "SELECT_ONE_FROM_FIELD_CODE", fieldCode="SIARU.TYPE"),
    "3": F(3, "Matériaux", "SELECT_MULTIPLE_FROM_FIELD_CODE", fieldCode="SIAS.MAT"),
    "4": F(4, "Poids", "MEASUREMENT", constraints={"min": 0, "max": 1000, "unit": "g"}),
    "5": F(5, "Année", "INTEGER"),
    "6": F(6, "Auteur", "SELECT_ONE_PERSON"),
    "7": F(7, "Date", "DATETIME"),
    "8": F(8, "Projet", "SELECT_ONE_ACTION_UNIT"),
}
VOCABS = {
    "SIARU.TYPE": Vocabulary([{"id": "10", "resolvedLabel": "Bâtiment"}, {"id": "11", "resolvedLabel": "Fossé"}]),
    "SIAS.MAT": Vocabulary([{"id": "20", "resolvedLabel": "Os"}, {"id": "21", "resolvedLabel": "Verre"},
                            {"id": "22", "resolvedLabel": "Dup"}, {"id": "23", "resolvedLabel": "dup"}]),
}


class SchemaTests(unittest.TestCase):
    def setUp(self):
        self.form = parse_form(LAYOUT, FIELDS)
        self.specs = flatten_schema(self.form)
        self.by = {s.name: s for s in self.specs}

    def test_columns_and_editability(self):
        self.assertEqual([s.name for s in self.specs],
                         ["Identifiant", "Type", "Matériaux", "Poids", "Année", "Auteur", "Date", "Projet"])
        self.assertTrue(self.by["Identifiant"].required)
        self.assertTrue(self.by["Type"].editable and self.by["Type"].vocab_code == "SIARU.TYPE")
        self.assertFalse(self.by["Auteur"].editable)  # référence : lecture seule en v1
        self.assertFalse(self.by["Projet"].editable)  # isReadOnly du layout
        self.assertEqual(self.by["Poids"].max, 1000)

    def test_duplicate_labels_and_merge(self):
        f2 = parse_form(LAYOUT, {**FIELDS, "5": F(5, "Poids", "INTEGER")})
        names = [s.name for s in flatten_schema(f2)]
        self.assertIn("Poids", names)
        self.assertIn("Poids (5)", names)
        merged = merge_schemas(self.specs, flatten_schema(parse_form(
            [{"name": "x", "rows": [{"columns": [{"fieldId": 99}]}]}], {"99": F(99, "Autre", "TEXT")})))
        self.assertEqual(len(merged), len(self.specs) + 1)

    def test_normalize(self):
        self.assertEqual(normalize("  Bâtiment "), normalize("batiment"))


class CellsTests(unittest.TestCase):
    def setUp(self):
        self.specs = flatten_schema(parse_form(LAYOUT, FIELDS))
        self.answers = {
            "1": {"answerType": "TEXT", "value": "UE1"},
            "2": {"answerType": "SELECT_ONE_FROM_FIELD_CODE", "value": {"resourceId": "10", "label": "x"}},
            "3": {"answerType": "SELECT_MULTIPLE_FROM_FIELD_CODE", "values": [{"resourceId": "20"}, {"resourceId": "21"}],
                  "total": 2, "complete": True},
            "4": {"answerType": "MEASUREMENT", "value": {"numericValue": 12.5, "symbol": "g"}},
            "6": {"answerType": "SELECT_ONE_PERSON", "value": {"resourceId": "5", "label": "Dupont"}},
            "7": "2026-01-02T00:00:00Z",
        }

    def test_to_cells_labels(self):
        cells = row_to_cells(self.specs, self.answers, VOCABS)
        self.assertEqual(cells["Type"], "Bâtiment")
        self.assertEqual(cells["Matériaux"], "Os;Verre")
        self.assertEqual(cells["Poids"], 12.5)
        self.assertEqual(cells["Auteur"], "Dupont")
        self.assertIsNone(cells["Année"])

    def test_unchanged_row_gives_empty_patch(self):
        base = row_to_cells(self.specs, self.answers, VOCABS)
        d = cells_to_patch(self.specs, base, dict(base), VOCABS)
        self.assertEqual(d.answers, {})
        self.assertTrue(d.ok)

    def test_label_resolution_to_ids(self):
        base = row_to_cells(self.specs, self.answers, VOCABS)
        cur = {**base, "Type": " fosse ", "Matériaux": "Verre; Os", "Poids": "13,5", "Année": "2020",
               "Date": "2026-03-04", "Identifiant": "UE2"}
        d = cells_to_patch(self.specs, base, cur, VOCABS)
        self.assertTrue(d.ok, d.issues)
        self.assertEqual(d.answers["2"], {"value": "11"})
        self.assertEqual(d.answers["3"], {"values": ["21", "20"]})
        self.assertEqual(d.answers["4"], {"value": 13.5})
        self.assertEqual(d.answers["5"], {"value": 2020})
        self.assertEqual(d.answers["7"], {"value": "2026-03-04T00:00:00Z"})
        self.assertEqual(d.answers["1"], {"value": "UE2"})

    def test_errors(self):
        base = row_to_cells(self.specs, self.answers, VOCABS)
        cur = {**base, "Type": "Inconnu", "Matériaux": "dup", "Poids": "5000", "Année": "1.5", "Date": "hier",
               "Identifiant": ""}
        d = cells_to_patch(self.specs, base, cur, VOCABS)
        msgs = {i.column: i.message for i in d.issues}
        self.assertIn("inconnu", msgs["Type"])
        self.assertIn("ambigu", msgs["Matériaux"])
        self.assertIn("maximum", msgs["Poids"])
        self.assertIn("entier", msgs["Année"])
        self.assertIn("date", msgs["Date"])
        self.assertIn("obligatoire", msgs["Identifiant"])
        self.assertFalse(d.ok)
        self.assertEqual(d.answers, {})

    def test_clear_cell_and_readonly(self):
        base = row_to_cells(self.specs, self.answers, VOCABS)
        cur = {**base, "Type": None, "Matériaux": "", "Auteur": "Autre"}
        d = cells_to_patch(self.specs, base, cur, VOCABS)
        self.assertEqual(d.answers["2"], {"value": None})
        self.assertEqual(d.answers["3"], {"values": []})
        self.assertNotIn("6", d.answers)
        self.assertTrue(d.ok)  # lecture seule = avertissement non bloquant
        self.assertTrue(any(not i.blocking and i.column == "Auteur" for i in d.issues))

    def test_incomplete_multi_uses_add_remove(self):
        base = row_to_cells(self.specs, self.answers, VOCABS)
        d = cells_to_patch(self.specs, base, {**base, "Matériaux": "Os;Verre;Dup"}, VOCABS, incomplete=["Matériaux"])
        # "Dup" est ambigu avec "dup" -> erreur, pas de choix au hasard
        self.assertFalse(d.ok)
        VOCABS2 = {**VOCABS, "SIAS.MAT": Vocabulary([{"id": "20", "resolvedLabel": "Os"},
                                                      {"id": "21", "resolvedLabel": "Verre"},
                                                      {"id": "24", "resolvedLabel": "Bois"}])}
        d = cells_to_patch(self.specs, base, {**base, "Matériaux": "Os;Bois"}, VOCABS2, incomplete=["Matériaux"])
        self.assertEqual(d.answers["3"], {"add": ["24"], "remove": ["21"]})


if __name__ == "__main__":
    unittest.main()
