#!/usr/bin/env python3
"""Disposable CI ONLY: real Spring Boot/MySQL native session after Hub redemption.

Never point DB_URL or API_URL at a production endpoint. Test fixture is generated
only after fresh Flyway migrations have finished in an isolated CI MySQL DB.
The central PULSE side is a local *mock*, so this is not cross-repo E2E.
"""
import json
import os
import secrets
import sys
import threading
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import pymysql

API = "http://127.0.0.1:18081"
MOCK = ("127.0.0.1", 18089)
WORLD = "tardis"
KEY = os.environ["PULSE_SSO_SERVICE_KEY"]
DB_PASS = os.environ["DB_PASSWORD"]
DB_USER = os.environ.get("DB_USERNAME", "tardis_ci")
seen = set()
member_id = 0


def db():
    return pymysql.connect(
        host="127.0.0.1", port=3307, user=DB_USER, password=DB_PASS,
        database="tardis", autocommit=True, connect_timeout=3
    )


class HubMock(BaseHTTPRequestHandler):
    def log_message(self, *args):
        return  # Do not log tickets or credentials.

    def do_POST(self):
        if self.path != "/api/pulse-id/sso/redeem":
            self.send_error(404)
            return
        length = int(self.headers.get("content-length", "0"))
        if length < 1 or length > 256:
            self.send_error(400)
            return
        try:
            payload = json.loads(self.rfile.read(length))
        except (ValueError, UnicodeDecodeError):
            self.send_error(400)
            return
        ticket = payload.get("ticket")
        with lock:
            valid = (
                self.headers.get("Authorization") == "Bearer " + KEY
                and payload.get("world") == WORLD and isinstance(ticket, str)
                and len(ticket) == 43 and ticket not in seen
            )
            if valid:
                seen.add(ticket)
        body = ({"world": WORLD, "legacyUserId": str(member_id)}
                if valid else {"error": "SSO_TICKET_INVALID"})
        raw = json.dumps(body).encode("utf8")
        self.send_response(200 if valid else 401)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)


lock = threading.Lock()


def call(method, path, body=None, bearer=None, cookie=None):
    headers = {"Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    if bearer:
        headers["Authorization"] = "Bearer " + bearer
    if cookie:
        headers["Cookie"] = cookie
    request = urllib.request.Request(
        API + path, method=method, headers=headers,
        data=json.dumps(body).encode("utf8") if body is not None else None
    )
    try:
        response = urllib.request.urlopen(request, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read()
        return response.status, json.loads(raw) if raw else {}, response.headers


def assert_true(value, message):
    if not value:
        raise AssertionError(message)


def run():
    global member_id
    connection = db()
    try:
        # This is an empty CI fixture; V9 must have been processed BEFORE this seed.
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT COUNT(*) FROM flyway_schema_history "
                "WHERE version = '9' AND success = 1"
            )
            assert_true(cursor.fetchone()[0] == 1,
                        "Dangerous historical Flyway V9 must be complete BEFORE fixture seeding")
            cursor.execute(
                "INSERT INTO member "
                "(username,password,name,email,pin,email_verified,pin_configured,password_login_enabled) "
                "VALUES (%s,%s,%s,%s,%s,b'1',b'1',b'1')",
                ("pulse_ci", "ci-only-unused-password", "CI Tester",
                 "pulse-ci@localhost.invalid", "ci-only-unused-pin")
            )
            member_id = cursor.lastrowid
            cursor.execute(
                "INSERT INTO wallet (member_id, balance) VALUES (%s, 100.00)",
                (member_id,)
            )
        server = ThreadingHTTPServer(MOCK, HubMock)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            status, _, _ = call("POST", "/api/auth/pulse-sso", {"ticket": "bad"})
            assert_true(status == 401, "Malformed ticket rejected")
            ticket = secrets.token_urlsafe(32)
            status, data, response_headers = call(
                "POST", "/api/auth/pulse-sso", {"ticket": ticket}
            )
            assert_true(status == 200, "Native Tardis SSO must succeed")
            assert_true(bool(data.get("token")), "Native JWT returned")
            assert_true(data.get("username") == "pulse_ci", "Identity not altered")
            assert_true(data.get("dailyReward") is True, "Daily reward granted once")
            cookie = response_headers.get("Set-Cookie", "").split(";", 1)[0]
            assert_true(cookie.startswith("refresh_token="), "Native refresh cookie returned")
            status, sessions, _ = call(
                "GET", "/api/auth/sessions", bearer=data["token"], cookie=cookie
            )
            assert_true(status == 200 and isinstance(sessions, list),
                        "Native JWT grants original protected sessions API")
            assert_true(len(sessions) == 1, "One refresh session is issued")
            status, _, _ = call("POST", "/api/auth/pulse-sso", {"ticket": ticket})
            assert_true(status == 401, "Used ticket cannot be redeemed again")
            with connection.cursor() as cursor:
                cursor.execute("SELECT balance FROM wallet WHERE member_id = %s", (member_id,))
                assert_true(str(cursor.fetchone()[0]) == "600.00", "Wallet daily reward preserved")
                cursor.execute(
                    "SELECT COUNT(*) FROM ledger_entry "
                    "WHERE member_id = %s AND type = 'DAILY_LOGIN_REWARD'", (member_id,)
                )
                assert_true(cursor.fetchone()[0] == 1, "Ledger reward exactly once")
            second_ticket = secrets.token_urlsafe(32)
            status, again, _ = call(
                "POST", "/api/auth/pulse-sso", {"ticket": second_ticket}
            )
            assert_true(status == 200, "Second valid ticket still logs in")
            assert_true(again.get("dailyReward") is False, "No duplicate daily reward")
            with connection.cursor() as cursor:
                cursor.execute("SELECT balance FROM wallet WHERE member_id = %s", (member_id,))
                assert_true(str(cursor.fetchone()[0]) == "600.00", "Wallet unchanged after second login")
                cursor.execute(
                    "SELECT COUNT(*) FROM ledger_entry "
                    "WHERE member_id = %s AND type = 'DAILY_LOGIN_REWARD'", (member_id,)
                )
                assert_true(cursor.fetchone()[0] == 1, "No duplicate ledger reward")
            # An authenticated central logout invalidates both native JWTs and
            # refresh cookies, including the original legacy password sessions.
            status, _, _ = call(
                "POST", "/api/auth/pulse-sso/revoke",
                {"world": "tardis", "legacyUserId": str(member_id)},
                bearer="wrong-service-key"
            )
            assert_true(status == 401, "Unknown server cannot revoke a member")
            status, _, _ = call(
                "POST", "/api/auth/pulse-sso/revoke",
                {"world": "interactive_brain", "legacyUserId": str(member_id)},
                bearer=KEY
            )
            assert_true(status == 400, "Cannot use Tardis key to revoke another World")
            status, _, _ = call(
                "POST", "/api/auth/pulse-sso/revoke",
                {"world": "tardis", "legacyUserId": str(member_id)},
                bearer=KEY
            )
            assert_true(status == 200, "Authorized central global logout accepted")
            status, _, _ = call(
                "GET", "/api/auth/sessions", bearer=data["token"], cookie=cookie
            )
            assert_true(status == 401, "Old Tardis JWT rejected after global logout")
            status, _, _ = call(
                "POST", "/api/auth/refresh", cookie=cookie
            )
            assert_true(status == 401, "Old Tardis refresh session revoked")
            with connection.cursor() as cursor:
                cursor.execute(
                    "SELECT session_generation FROM member WHERE id = %s", (member_id,)
                )
                assert_true(cursor.fetchone()[0] == 1, "Member revocation persisted in MySQL")
                cursor.execute(
                    "SELECT COUNT(*) FROM refresh_token WHERE member_id = %s", (member_id,)
                )
                assert_true(cursor.fetchone()[0] == 0, "All native refresh sessions revoked")
            third_ticket = secrets.token_urlsafe(32)
            status, resumed, _ = call(
                "POST", "/api/auth/pulse-sso", {"ticket": third_ticket}
            )
            assert_true(status == 200, "A new independently verified login may resume")
            status, _, _ = call(
                "GET", "/api/auth/sessions", bearer=resumed["token"]
            )
            assert_true(status == 200, "New-generation JWT is accepted")
            assert_true(resumed.get("dailyReward") is False, "Global logout must not duplicate wallet reward")
            print("TARDIS REAL MYSQL SSO PASS: member/JWT/refresh, wallet/ledger, replay, global logout invalidation and fresh login.")
        finally:
            server.shutdown()
            server.server_close()
    finally:
        connection.close()


if __name__ == "__main__":
    if os.environ.get("PULSE_DISPOSABLE_DB_TEST") != "1":
        sys.exit("Refusing to run without PULSE_DISPOSABLE_DB_TEST=1")
    run()
