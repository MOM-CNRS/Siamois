import json
import unittest

from siamois_sdk import (
    AuthenticationError, ConflictError, NetworkError, SiamoisClient, build_patch_answers, parse_form,
)
from siamois_sdk.forms import display_value, diff_multi
import requests


class FakeResponse:
    def __init__(self, status=200, body=None, headers=None):
        self.status_code = status
        self._body = body
        self.content = b"" if body is None else json.dumps(body).encode()
        self.headers = headers or {}

    def json(self):
        return self._body


class FakeSession:
    def __init__(self, routes):
        self.routes = routes  # (method, path) -> FakeResponse | callable | Exception
        self.calls = []

    def request(self, method, url, params=None, json=None, headers=None, timeout=None):
        path = url.split("/api/v1", 1)[1]
        self.calls.append((method, path, params, json, headers))
        r = self.routes[(method, path)]
        if isinstance(r, Exception):
            raise r
        return r(params, json) if callable(r) else r


LAYOUT = json.dumps([{
    "name": "Général", "rows": [
        {"columns": [
            {"fieldId": -1, "width": {"span": 6}, "isRequired": True, "isReadOnly": False},
            {"fieldId": -2, "width": {"span": 6}, "isReadOnly": True},
        ]},
        {"columns": [{"fieldId": 3, "width": {"span": 12}}]},
    ]}])
FIELDS = {
    "-1": {"id": "-1", "label": "Identifiant", "answerType": "TEXT"},
    "-2": {"id": "-2", "label": "Type", "answerType": "SELECT_ONE_FROM_FIELD_CODE", "fieldCode": "SIARU.TYPE"},
    "3": {"id": "3", "label": "Auteurs", "answerType": "SELECT_MULTIPLE_PERSON"},
}


def client(routes):
    c = SiamoisClient("https://x.test/siamois", session=FakeSession(routes))
    return c


class LoginTests(unittest.TestCase):
    def test_login_sets_token_and_sends_bearer(self):
        login = FakeResponse(200, {"accessToken": "T", "expiresIn": 3600, "tokenType": "Bearer",
                                   "user": {"id": 1, "username": "u", "organizations": [{"id": 2, "name": "O"}]}})
        orgs = FakeResponse(200, {"data": [{"id": "2", "name": "O", "_permissions": {"canCreateProjects": True}}],
                                  "meta": {"total": 1, "limit": 100, "offset": 0}})
        c = client({("POST", "/auth/login"): login, ("GET", "/organizations"): orgs})
        user = c.login("a@b.c", "pw")
        self.assertEqual(user.organizations[0].name, "O")
        self.assertTrue(c.is_authenticated)
        page = c.organizations()
        self.assertTrue(page.items[0].can_create_projects)
        self.assertEqual(c._session.calls[1][4]["Authorization"], "Bearer T")
        self.assertEqual(c._session.calls[0][3], {"email": "a@b.c", "password": "pw"})
        self.assertNotIn("Authorization", c._session.calls[0][4])

    def test_401_and_network_errors(self):
        c = client({("POST", "/auth/login"): FakeResponse(401, {"error": "unauthorized", "message": "bad"}),
                    ("GET", "/organizations"): requests.ConnectTimeout("t")})
        with self.assertRaises(AuthenticationError):
            c.login("a", "b")
        with self.assertRaises(NetworkError) as cm:
            c.organizations()
        self.assertIn("ne répond pas", cm.exception.user_message)


class PaginationTests(unittest.TestCase):
    def test_iter_all_pages_and_progress(self):
        def projects(params, _):
            off, lim = params["offset"], params["limit"]
            data = [{"id": str(i), "name": f"p{i}"} for i in range(off, min(off + lim, 5))]
            return FakeResponse(200, {"data": data, "meta": {"total": 5, "limit": lim, "offset": off}})

        c = client({("GET", "/projects"): projects})
        seen = []
        items = list(c.iter_all(lambda **kw: c.projects(organization_id=2, **kw), page_size=2,
                                on_progress=lambda d, t: seen.append((d, t))))
        self.assertEqual([p.id for p in items], ["0", "1", "2", "3", "4"])
        self.assertEqual(seen, [(2, 5), (4, 5), (5, 5)])

    def test_iter_all_cancel(self):
        def projects(params, _):
            return FakeResponse(200, {"data": [{"id": "1", "name": "a"}], "meta": {"total": 10, "limit": 1, "offset": 0}})
        c = client({("GET", "/projects"): projects})
        items = list(c.iter_all(c.projects, page_size=1, on_progress=lambda d, t: False))
        self.assertEqual(len(items), 1)


class FormTests(unittest.TestCase):
    def test_parse_layout(self):
        form = parse_form(LAYOUT, FIELDS)
        self.assertEqual(form.field_ids(), ["-1", "-2", "3"])
        cols = list(form.columns())
        self.assertTrue(cols[0].required)
        self.assertEqual(cols[0].span, 6)
        self.assertTrue(cols[1].read_only)
        self.assertTrue(form.field("-2").is_select_one)
        self.assertTrue(form.field("3").is_select_many)

    def test_types_endpoint(self):
        body = {"data": [{"id": "42", "formBundle": {"layoutJson": LAYOUT}, "fields": FIELDS}],
                "_default": {"formBundle": {"layoutJson": LAYOUT}, "fields": FIELDS}, "fields": FIELDS}
        c = client({("GET", "/projects/7/recording-unit-types"): FakeResponse(200, body)})
        default, by_type = c.recording_unit_forms(7)
        self.assertEqual(default.field_ids(), ["-1", "-2", "3"])
        self.assertIn("42", by_type)

    def test_patch_answers_only_changed_and_not_readonly(self):
        form = parse_form(LAYOUT, FIELDS)
        original = {
            "-1": {"answerType": "TEXT", "value": "UE1"},
            "-2": {"answerType": "SELECT_ONE_FROM_FIELD_CODE", "value": {"resourceId": "5", "label": "BAT"}},
            "3": {"answerType": "SELECT_MULTIPLE_PERSON", "values": [{"resourceId": "1"}], "total": 3,
                  "complete": False},
        }
        edited = {"-1": "UE2", "-2": "9", "3": ["1", "4"]}
        out = build_patch_answers(form, edited, original)
        self.assertEqual(out["-1"], {"value": "UE2"})
        self.assertNotIn("-2", out)  # lecture seule dans le layout
        self.assertEqual(out["3"], {"add": ["4"], "remove": []})  # incomplet -> add/remove
        # inchangé -> rien
        self.assertEqual(build_patch_answers(form, {"-1": "UE1"}, original), {})

    def test_complete_multi_uses_values(self):
        form = parse_form(LAYOUT, FIELDS)
        original = {"3": {"values": [{"resourceId": "1"}], "total": 1, "complete": True}}
        self.assertEqual(build_patch_answers(form, {"3": ["1", "2"]}, original), {"3": {"values": ["1", "2"]}})

    def test_display_and_diff(self):
        self.assertEqual(display_value({"answerType": "TEXT", "value": "x"}), "x")
        self.assertEqual(display_value({"values": [{"resourceId": "1", "label": "A"}, {"resourceId": "2", "label": "B"}],
                                        "total": 2, "complete": True}), "A, B")
        self.assertEqual(diff_multi(["1"], ["1", "2"]), {"add": ["2"], "remove": []})


class UpdateTests(unittest.TestCase):
    def test_update_ru_payload_and_conflict(self):
        calls = {}

        def patch(params, body):
            calls["body"] = body
            return FakeResponse(409, {"data": {"resourceType": "recording-units", "resourceId": "9",
                                               "expectedRevision": 3, "currentRevision": 5,
                                               "serverState": {"id": "9"}}})

        c = client({("PATCH", "/recording-units/9"): patch})
        geom = {"type": "Point", "srid": 4326, "coordinates": [1, 2]}
        with self.assertRaises(ConflictError) as cm:
            c.update_recording_unit(9, answers={"-1": {"value": "x"}}, geom=geom, expected_revision=3)
        self.assertEqual(calls["body"], {"answers": {"-1": {"value": "x"}}, "geom": geom, "expectedRevision": 3})
        self.assertTrue(cm.exception.is_revision_conflict)
        self.assertEqual(cm.exception.current_revision, 5)

    def test_geom_unchanged_by_default_and_null_deletes(self):
        bodies = []

        def patch(params, body):
            bodies.append(body)
            return FakeResponse(200, {"data": {"id": "9", "syncRevision": 6}})

        c = client({("PATCH", "/recording-units/9"): patch})
        c.update_recording_unit(9, validated="COMPLETE")
        c.update_recording_unit(9, geom=None)
        self.assertEqual(bodies, [{"validated": "COMPLETE"}, {"geom": None}])


if __name__ == "__main__":
    unittest.main()
