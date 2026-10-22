"""End-to-end tests against a running stack (scripts/start-all.sh). Run: python3 tests/e2e_test.py"""
import json
import os
import unittest
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get("GATEWAY_URL", "http://localhost:8080/api/v1")
INVITE = os.environ.get("TEACHER_INVITE_CODE", "sdu-teacher")


def call(method, path, body=None, token=None):
    request = urllib.request.Request(BASE + path, method=method, data=None if body is None else json.dumps(body).encode(),
                                     headers={"Content-Type": "application/json", **({"Authorization": f"Bearer {token}"} if token else {})})
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, json.loads(response.read() or "null")
    except urllib.error.HTTPError as error:
        with error:
            return error.code, json.loads(error.read() or "null")


def register(role):
    email = f"{role.lower()}-{uuid.uuid4().hex[:8]}@sdu.edu.kz"
    status, body = call("POST", "/auth/register", {"name": f"Test {role.title()}", "email": email, "password": "password123",
                                                    "role": role, "inviteCode": INVITE if role == "TEACHER" else ""})
    assert status == 201, body
    return body["token"], body["user"]


class AuthTests(unittest.TestCase):
    def test_all_services_healthy(self):
        status, body = call("GET", "/health")
        self.assertEqual(status, 200)
        self.assertEqual(set(body.values()), {"ok"})

    def test_login_rejects_wrong_password_and_protects_routes(self):
        self.assertEqual(call("POST", "/auth/login", {"email": "student@sdu.edu.kz", "password": "nope-nope"})[0], 401)
        self.assertEqual(call("GET", "/rooms")[0], 401)
        self.assertEqual(call("GET", "/rooms", token="bad.token")[0], 401)

    def test_teacher_registration_needs_invite_code(self):
        status, _ = call("POST", "/auth/register", {"name": "X", "email": f"{uuid.uuid4().hex[:6]}@sdu.edu.kz", "password": "password123", "role": "TEACHER"})
        self.assertEqual(status, 403)


class CampusPlusTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.student, cls.student_user = register("STUDENT")
        cls.teacher, cls.teacher_user = register("TEACHER")

    def test_campus_has_only_blocks_d_to_i(self):
        _, buildings = call("GET", "/buildings", token=self.student)
        self.assertEqual([b["id"] for b in buildings], list("DEFGHI"))

    def test_free_rooms_come_through_the_timetable_adapter(self):
        _, all_rooms = call("GET", "/rooms?at=09:00&day=2", token=self.student)
        _, evening = call("GET", "/rooms?at=17:30&day=2", token=self.student)
        self.assertTrue(0 < len(all_rooms) < 101, "some rooms are busy at 09:00 on a weekday")
        self.assertTrue(all(r["freeUntil"] > "09:00" for r in all_rooms))
        self.assertTrue(evening and all(r["freeUntil"] == "18:00" or r["freeUntil"] > "17:30" for r in evening))

    def test_room_filters_and_barrels(self):
        _, found = call("GET", "/rooms?q=D117&day=6", token=self.student)
        self.assertEqual([(r["id"], r["kind"]) for r in found], [("D117", "Barrel")])
        _, blockh = call("GET", "/rooms?building=H&day=6", token=self.student)
        self.assertEqual({r["building"] for r in blockh}, {"H"})
        self.assertEqual(len(blockh), 18)  # 15 classrooms + H01..H03

    def test_office_hours_rules(self):
        self.assertEqual(call("PUT", "/teachers/me", {"department": "CS", "office": "E301"}, self.teacher)[0], 200)
        slot = {"day": "Fri", "start": "10:00", "end": "11:00", "capacity": 1}
        status, created = call("POST", "/office-hours", slot, self.teacher)
        self.assertEqual(status, 201)
        self.assertEqual(call("POST", "/office-hours", {**slot, "start": "10:30", "end": "11:30"}, self.teacher)[0], 409, "overlap")
        self.assertEqual(call("POST", "/office-hours", slot, self.student)[0], 403, "students cannot create")
        self.assertEqual(call("POST", "/office-hours/book", {"id": created["id"]}, self.student)[0], 200)
        self.assertEqual(call("POST", "/office-hours/book", {"id": created["id"]}, self.student)[0], 409, "double booking")
        other, _ = register("STUDENT")
        self.assertEqual(call("POST", "/office-hours/book", {"id": created["id"]}, other)[0], 409, "full")

    def test_strategy_pattern_selects_slots_at_runtime(self):
        teacher, user = register("TEACHER")
        call("PUT", "/teachers/me", {"department": "Math", "office": "D302"}, teacher)
        for day, start in (("Wed", "15:00"), ("Mon", "09:00"), ("Thu", "12:00")):
            call("POST", "/office-hours", {"day": day, "start": start, "end": f"{int(start[:2]) + 1}:00".zfill(5), "capacity": 5}, teacher)
        student, _ = register("STUDENT")
        pick = lambda strategy, **extra: call("POST", "/office-hours/book", {"teacherId": user["id"], "strategy": strategy, **extra}, student)
        status, first = pick("first")
        self.assertEqual((status, first["day"], first["start"]), (200, "Mon", "09:00"))
        status, preferred = pick("preferred", preferredTime="14:30")
        self.assertEqual((status, preferred["start"]), (200, "15:00"))
        self.assertEqual(pick("deadline", deadline="2000-01-01")[0], 409, "nothing before an expired deadline")
        self.assertEqual(pick("nonsense")[0], 422)


class FixMyCampusTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.student, _ = register("STUDENT")
        cls.teacher, _ = register("TEACHER")

    def test_decorator_pipeline_validates_alerts_and_notifies(self):
        report = {"roomId": "G205", "category": "Lighting", "description": "Two tubes flicker in the front row."}
        _, everything = call("GET", "/issues?scope=all", token=self.teacher)  # clear leftovers so the test is repeatable
        for old in (i for i in everything if (i["roomId"], i["category"]) == ("G205", "Lighting") and i["status"] != "RESOLVED"):
            call("POST", f"/issues/{old['id']}/status", {"status": "RESOLVED"}, self.teacher)
        status, first = call("POST", "/issues", report, self.student)
        self.assertEqual((status, first["duplicateOf"]), (201, None))
        other, _ = register("STUDENT")
        status, second = call("POST", "/issues", report, other)
        self.assertEqual((status, second["duplicateOf"]), (201, first["id"]))
        self.assertIn("warning", second)
        _, mine = call("GET", "/issues/notifications", token=self.student)
        self.assertTrue(any("received" in m for m in mine) and any("same Lighting" in m for m in mine))

    def test_validation_and_service_boundary(self):
        self.assertEqual(call("POST", "/issues", {"roomId": "G205", "category": "Lighting", "description": "short"}, self.student)[0], 422)
        self.assertEqual(call("POST", "/issues", {"roomId": "Z999", "category": "Lighting", "description": "A real description here."}, self.student)[0], 422)
        self.assertEqual(call("POST", "/issues", {"roomId": "G205", "category": "Magic", "description": "A real description here."}, self.student)[0], 422)

    def test_only_teachers_review(self):
        _, created = call("POST", "/issues", {"roomId": "I101", "category": "Network", "description": "No wifi in the whole room."}, self.student)
        self.assertEqual(call("POST", f"/issues/{created['id']}/status", {"status": "IN_PROGRESS"}, self.student)[0], 403)
        status, updated = call("POST", f"/issues/{created['id']}/status", {"status": "IN_PROGRESS"}, self.teacher)
        self.assertEqual((status, updated["status"]), (200, "IN_PROGRESS"))
        _, listing = call("GET", "/issues", token=self.student)
        self.assertEqual(next(i for i in listing if i["id"] == created["id"])["status"], "IN_PROGRESS")


class ProjectForgeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.student, _ = register("STUDENT")

    def test_initiative_lifecycle(self):
        status, created = call("POST", "/initiatives", {"title": f"Chess {uuid.uuid4().hex[:4]}", "category": "Event", "summary": "Weekly games", "contact": "chess@sdu.edu.kz"}, self.student)
        self.assertEqual(status, 201)
        _, followed = call("POST", "/initiatives/follow", {"id": created["id"]}, self.student)
        self.assertEqual((followed["following"], followed["followers"]), (True, 1))
        _, unfollowed = call("POST", "/initiatives/follow", {"id": created["id"]}, self.student)
        self.assertEqual((unfollowed["following"], unfollowed["followers"]), (False, 0))
        self.assertEqual(call("POST", "/initiatives", {"title": "x", "category": "Nope", "summary": "y", "contact": "bad"}, self.student)[0], 422)

    def test_recommendation_strategies_switch_at_runtime(self):
        tops = {s: call("GET", f"/recommendations?strategy={s}", token=self.student)[1][0]["title"] for s in ("popularity", "recent", "category")}
        self.assertEqual(tops["popularity"], "SDU Hackathon 2026")
        self.assertEqual(call("GET", "/recommendations?strategy=bogus", token=self.student)[0], 422)

    def test_clubs_and_profile(self):
        _, clubs = call("GET", "/clubs", token=self.student)
        club = clubs[0]
        _, joined = call("POST", "/clubs/toggle", {"id": club["id"]}, self.student)
        self.assertNotEqual(joined["joined"], club["joined"])
        status, saved = call("PUT", "/profile", {"phone": "+7 701 111 22 33", "telegram": "@tester"}, self.student)
        self.assertEqual((status, saved["telegram"]), (200, "@tester"))
        call("PUT", "/profile", {"notifications": {"grades": False}}, self.student)
        _, overview = call("GET", "/profile", token=self.student)
        self.assertFalse(overview["profile"]["notifications"]["grades"])
        self.assertTrue(all(f["kind"] != "grades" for f in overview["feed"]))
        self.assertEqual(call("PUT", "/profile", {"photo": "x" * 1_600_000}, self.student)[0], 413)


if __name__ == "__main__":
    unittest.main(verbosity=2)
