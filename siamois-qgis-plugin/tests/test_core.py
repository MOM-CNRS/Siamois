import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "siamois-sdk-python"))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from siamois_sdk import ConflictError, parse_form  # noqa: E402
from siamois_sdk.flatten import Vocabulary  # noqa: E402
from siamois_sdk.models import Find, Page, Project, RecordingUnit  # noqa: E402
from siamois_qgis.core import loader, syncplan  # noqa: E402
from siamois_qgis.core.geo import geojson_to_wkt, dominant_srid  # noqa: E402
from siamois_qgis.core.store import Store  # noqa: E402

LAYOUT = [{"name": "Général", "rows": [{"columns": [
    {"fieldId": 1, "isRequired": True}, {"fieldId": 2}, {"fieldId": 3}]}]}]
FIELDS = {
    "1": {"id": "1", "label": "Nom", "answerType": "TEXT"},
    "2": {"id": "2", "label": "Type", "answerType": "SELECT_ONE_FROM_FIELD_CODE", "fieldCode": "SIARU.TYPE"},
    "3": {"id": "3", "label": "Poids", "answerType": "MEASUREMENT"},
}
PT = {"type": "Point", "srid": 2154, "coordinates": [100.0, 200.5]}


class FakeClient:
    base_url = "https://x.test"

    def __init__(self):
        self.patches = []
        self.fail_conflict = False

    def project(self, pid, fields="all"):
        return Project(id="7", name="P", full_identifier="P-7", validated="INCOMPLETE", answers={"1": "Mon projet"})

    def project_form(self, org):
        return parse_form(LAYOUT, FIELDS)

    def recording_unit_forms(self, pid):
        return parse_form(LAYOUT, FIELDS), {}

    def find_forms(self, pid):
        return parse_form(LAYOUT, FIELDS), {}

    def vocabulary(self, pid, code):
        return Vocabulary([{"id": "10", "resolvedLabel": "Bâtiment"}, {"id": "11", "resolvedLabel": "Fossé"}])

    @staticmethod
    def iter_all(fetch, page_size=100, on_progress=None):
        page = fetch(offset=0, limit=100)
        for i in page.items:
            yield i
        if on_progress:
            on_progress(len(page.items), page.total)

    def recording_units(self, pid, offset=0, limit=100):
        ru = RecordingUnit(id="9", full_identifier="UE-9", validated="COMPLETE", sync_revision=3, geom=PT,
                           answers={"1": "UE neuf", "2": {"resourceId": "10", "label": "x"}})
        return Page([ru], 1, limit, offset)

    def finds(self, pid, offset=0, limit=100):
        f = Find(id="5", full_identifier="M-5", validated="INCOMPLETE", geom=PT, answers={"1": "Tesson"})
        return Page([f], 1, limit, offset)

    def update_recording_unit(self, id_, **kw):
        self.patches.append(("ru", id_, kw))
        if self.fail_conflict:
            raise ConflictError(None, 409, {"data": {"expectedRevision": 3, "currentRevision": 5, "serverState": {}}})
        return RecordingUnit(id=id_, sync_revision=4)

    def update_find(self, id_, **kw):
        self.patches.append(("find", id_, kw))

    def update_project(self, id_, **kw):
        self.patches.append(("project", id_, kw))


class LoaderTests(unittest.TestCase):
    def test_load_project(self):
        steps = []
        b = loader.load_project(FakeClient(), 1, 7, lambda *a: steps.append(a) or True)
        self.assertEqual([l.name for l in b.layers], ["projet", "unites_enregistrement", "mobilier"])
        ru = b.layers[1]
        names = [s.name for s in ru.specs]
        self.assertEqual(names[:2], ["Identifiant complet", "Statut"])
        self.assertIn("Nom", names)
        row = ru.rows[0]
        self.assertEqual(row.cells["Type"], "Bâtiment")      # id -> libellé
        self.assertEqual(row.cells["Statut"], "Terminé")
        self.assertEqual(row.cells["Identifiant complet"], "UE-9")
        self.assertEqual(ru.srid, 2154)
        self.assertEqual(row.revision, 3)
        self.assertFalse(b.layers[2].geom_editable)  # mobilier : géométrie en lecture seule
        self.assertIn("SIARU.TYPE", b.vocab_concepts)

    def test_cancel(self):
        with self.assertRaises(loader.Cancelled):
            loader.load_project(FakeClient(), 1, 7, lambda *a: False)

    def test_geo(self):
        self.assertEqual(geojson_to_wkt(PT), "POINT (100 200.5)")
        poly = {"type": "Polygon", "coordinates": [[[0, 0], [1, 0], [1, 1], [0, 0]]]}
        self.assertEqual(geojson_to_wkt(poly), "POLYGON ((0 0, 1 0, 1 1, 0 0))")
        self.assertEqual(dominant_srid([PT, None, PT, {"type": "Point", "coordinates": [0, 0]}]), 2154)


class SyncTests(unittest.TestCase):
    def setUp(self):
        self.client = FakeClient()
        self.bundle = loader.load_project(self.client, 1, 7)
        self.ru = self.bundle.layers[1]
        self.vocabs = {c: Vocabulary(v) for c, v in self.bundle.vocab_concepts.items()}
        self.vocabs[loader.VOCAB_STATUS] = loader.status_vocabulary()
        r = self.ru.rows[0]
        self.base = {r.id: {"cells": dict(r.cells), "geom_wkt": geojson_to_wkt(r.geom), "revision": r.revision,
                            "incomplete": [], "allowed": r.allowed}}

    def current(self, **changes):
        cells = {**self.base["9"]["cells"], **changes}
        return {"9": {"cells": cells, "wkt": self.base["9"]["geom_wkt"], "geojson": PT}}

    def test_no_change_no_row(self):
        self.assertEqual(syncplan.collect_changes("ue", "recording_unit", self.ru.specs, self.base, self.current(),
                                                  self.vocabs, True), [])

    def test_changes_and_push(self):
        cur = self.current(Type="Fossé", Statut="En cours")
        cur["9"]["wkt"] = "POINT (1 1)"
        ch = syncplan.collect_changes("ue", "recording_unit", self.ru.specs, self.base, cur, self.vocabs, True)[0]
        self.assertTrue(ch.has_changes and not ch.blocking)
        self.assertEqual(ch.answers, {"2": {"value": "11"}})
        self.assertEqual(ch.validated, "INCOMPLETE")
        self.assertTrue(ch.geom_changed)
        res = syncplan.push(self.client, ch)
        self.assertEqual(res.status, "ok")
        self.assertEqual(res.new_revision, 4)
        kind, id_, kw = self.client.patches[0]
        self.assertEqual(kw["expected_revision"], 3)
        self.assertEqual(kw["validated"], "INCOMPLETE")

    def test_validation_errors_block(self):
        ch = syncplan.collect_changes("ue", "recording_unit", self.ru.specs, self.base,
                                      self.current(Type="Inexistant"), self.vocabs, True)[0]
        self.assertTrue(ch.blocking)
        self.assertIn("inconnu", ch.issues[0].message)

    def test_readonly_geom_for_finds(self):
        cur = self.current()
        cur["9"]["wkt"] = "POINT (5 5)"
        ch = syncplan.collect_changes("mobilier", "find", self.ru.specs, self.base, cur, self.vocabs, False)[0]
        self.assertFalse(ch.geom_changed)
        self.assertFalse(ch.blocking)

    def test_conflict(self):
        self.client.fail_conflict = True
        ch = syncplan.collect_changes("ue", "recording_unit", self.ru.specs, self.base,
                                      self.current(Type="Fossé"), self.vocabs, True)[0]
        res = syncplan.push(self.client, ch)
        self.assertEqual(res.status, "conflict")
        self.assertEqual(res.conflict.current_revision, 5)
        self.client.fail_conflict = False
        res = syncplan.push(self.client, ch, force_revision=5)  # garder local
        self.assertEqual(res.status, "ok")
        self.assertEqual(self.client.patches[-1][2]["expected_revision"], 5)


class StoreTests(unittest.TestCase):
    def test_roundtrip(self):
        b = loader.load_project(FakeClient(), 1, 7)
        with tempfile.TemporaryDirectory() as d:
            s = Store(os.path.join(d, "p.siamois.db"))
            l = b.layers[1]
            s.save_layer(l.name, l.kind, l.srid, l.specs)
            s.save_base(l.name, [{"id": r.id, "cells": r.cells, "geom_wkt": "POINT (1 2)", "revision": r.revision,
                                  "incomplete": r.incomplete, "allowed": r.allowed} for r in l.rows])
            s.save_vocab("SIARU.TYPE", b.vocab_concepts["SIARU.TYPE"])
            s.set_meta("project_id", "7")
            self.assertEqual(s.layer(l.name)["columns"][0].name, "Identifiant complet")
            base = s.base(l.name)["9"]
            self.assertEqual(base["revision"], 3)
            self.assertEqual(base["cells"]["Type"], "Bâtiment")
            self.assertEqual(s.vocabularies()["SIARU.TYPE"].resolve("fosse"), "11")
            s.update_base(l.name, "9", {"x": 1}, None, 9)
            self.assertEqual(s.base(l.name)["9"]["revision"], 9)
            s.close()


if __name__ == "__main__":
    unittest.main()
