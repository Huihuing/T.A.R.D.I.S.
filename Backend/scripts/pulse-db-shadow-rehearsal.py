#!/usr/bin/env python3
"""Fail-closed shadow snapshot/restore rehearsal for DISPOSABLE Tardis CI only.

All data are synthetic, created by pulse-sso-mysql-e2e.py.  No cloud credentials,
Aiven endpoints, production databases or remote servers are accepted.

Modes:
  prepare  - fingerprint fresh source schema and synthetic rows immediately
             BEFORE mysqldump (including flyway_schema_history/V9).
  verify   - assert complete shadow restored rows/schema/FKs/checksums match.
  guard    - reject an intentionally broken shadow without Flyway V9 history.
"""
import datetime
import decimal
import hashlib
import json
import os
import pathlib
import sys

import pymysql

SOURCE = "tardis"
SHADOW = "tardis_shadow_ci"
BROKEN = "tardis_broken_ci"
MANIFEST = pathlib.Path("/tmp/pulse-tardis-shadow-ci-fingerprints.json")
MAX_ROWS = 500  # Test data are deliberately tiny; never snapshot real data.
MAX_TABLES = 96


def require_ci():
    if os.getenv("PULSE_DISPOSABLE_DB_TEST") != "1" or os.getenv("GITHUB_ACTIONS") != "true":
        raise RuntimeError("SHADOW_REHEARSAL_DISPOSABLE_CI_ONLY")
    if os.getenv("MYSQL_HOST", "127.0.0.1") not in ("127.0.0.1", "localhost"):
        raise RuntimeError("SHADOW_REHEARSAL_LOOPBACK_ONLY")
    if os.getenv("MYSQL_PORT", "3307") != "3307":
        raise RuntimeError("SHADOW_REHEARSAL_FIXED_PORT_ONLY")
    if not os.getenv("DB_PASSWORD") or not os.getenv("DB_USERNAME"):
        raise RuntimeError("SHADOW_REHEARSAL_CREDENTIALS_REQUIRED")


def connect(schema):
    if schema not in (SOURCE, SHADOW, BROKEN):
        raise ValueError("UNKNOWN_SHADOW_SCHEMA")
    return pymysql.connect(
        host="127.0.0.1", port=3307,
        user=os.environ["DB_USERNAME"],
        password=os.environ["DB_PASSWORD"],
        database=schema,
        charset="utf8mb4", init_command="SET time_zone = '+00:00'",
        cursorclass=pymysql.cursors.DictCursor, autocommit=True,
        connect_timeout=4, read_timeout=10, write_timeout=10
    )


def normalized(value):
    if isinstance(value, bytes):
        return {"binary_hex": value.hex()}
    if isinstance(value, decimal.Decimal):
        return {"decimal": str(value)}
    if isinstance(value, (datetime.datetime, datetime.date, datetime.time)):
        return value.isoformat()
    if value is None or isinstance(value, (int, float, bool, str)):
        return value
    raise ValueError("UNSUPPORTED_COLUMN_TYPE")


def digest_rows(cursor, table):
    # Tables are introspected from INFORMATION_SCHEMA, never browser input.
    if not table.replace("_", "").isalnum():
        raise ValueError("INVALID_TABLE_NAME")
    cursor.execute("SELECT * FROM `" + table + "`")
    columns = [description[0] for description in cursor.description]
    data = cursor.fetchall()
    if len(data) > MAX_ROWS:
        raise RuntimeError("REFUSING_NON_SYNTHETIC_TABLE")
    canonical = [
        json.dumps([normalized(row[column]) for column in columns],
                   sort_keys=True, separators=(",", ":"), ensure_ascii=False)
        for row in data
    ]
    canonical.sort()
    h = hashlib.sha256()
    for line in canonical:
        h.update(line.encode("utf8"))
        h.update(b"\n")
    return {"count": len(canonical), "sha256": h.hexdigest(), "columns": columns}


def assert_flyway_history(cursor):
    cursor.execute(
        "SELECT version, checksum, success FROM flyway_schema_history "
        "WHERE version IS NOT NULL ORDER BY installed_rank"
    )
    history = cursor.fetchall()
    versions = {str(row["version"]): row for row in history}
    if "9" not in versions or int(versions["9"]["success"]) != 1:
        raise RuntimeError("DANGEROUS_V9_HISTORY_MISSING_STOP")
    if "12" not in versions or int(versions["12"]["success"]) != 1:
        raise RuntimeError("LATEST_FLYWAY_HISTORY_MISSING_STOP")
    if len(history) < 12:
        raise RuntimeError("INCOMPLETE_FLYWAY_HISTORY_STOP")
    return [
        {"version": str(row["version"]), "checksum": row["checksum"],
         "success": int(row["success"])}
        for row in history
    ]


def inventory(schema):
    with connect(schema) as connection:
        with connection.cursor() as cursor:
            history = assert_flyway_history(cursor)
            cursor.execute(
                "SELECT TABLE_NAME AS name FROM information_schema.TABLES "
                "WHERE TABLE_SCHEMA = %s AND TABLE_TYPE = 'BASE TABLE' "
                "ORDER BY TABLE_NAME", (schema,)
            )
            tables = [row["name"] for row in cursor.fetchall()]
            if not tables or len(tables) > MAX_TABLES:
                raise RuntimeError("SHADOW_SCHEMA_SIZE_UNEXPECTED")
            fingerprint = {}
            for table in tables:
                fingerprint[table] = digest_rows(cursor, table)
            for must in ("member", "wallet", "ledger_entry", "flyway_schema_history",
                         "pulse_logout_receipts"):
                if must not in fingerprint:
                    raise RuntimeError("SHADOW_MISSING_REQUIRED_TABLE")
            if any(fingerprint[x]["count"] < 1 for x in ("member", "wallet", "ledger_entry")):
                raise RuntimeError("SYNTHETIC_FINANCE_FIXTURE_MISSING")
            cursor.execute(
                "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, "
                "COLUMN_DEFAULT, EXTRA, ORDINAL_POSITION FROM information_schema.COLUMNS "
                "WHERE TABLE_SCHEMA = %s ORDER BY TABLE_NAME, ORDINAL_POSITION", (schema,)
            )
            columns = [
                [normalized(row[key]) for key in
                 ("TABLE_NAME", "COLUMN_NAME", "COLUMN_TYPE", "IS_NULLABLE",
                  "COLUMN_DEFAULT", "EXTRA", "ORDINAL_POSITION")]
                for row in cursor.fetchall()
            ]
            cursor.execute(
                "SELECT TABLE_NAME, COLUMN_NAME, CONSTRAINT_NAME, "
                "REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME "
                "FROM information_schema.KEY_COLUMN_USAGE "
                "WHERE TABLE_SCHEMA = %s AND REFERENCED_TABLE_NAME IS NOT NULL "
                "ORDER BY TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION",
                (schema,)
            )
            foreign_keys = [
                [row[key] for key in
                 ("TABLE_NAME", "COLUMN_NAME", "CONSTRAINT_NAME",
                  "REFERENCED_TABLE_NAME", "REFERENCED_COLUMN_NAME")]
                for row in cursor.fetchall()
            ]
            if not foreign_keys:
                raise RuntimeError("SHADOW_NO_FOREIGN_KEYS_STOP")
            cursor.execute(
                "SELECT member_id, balance FROM wallet ORDER BY member_id"
            )
            wallet = cursor.fetchall()
            cursor.execute(
                "SELECT member_id, amount, type, balance_after "
                "FROM ledger_entry ORDER BY member_id, id"
            )
            ledger = cursor.fetchall()
            return {
                "history": history, "tables": fingerprint,
                "columns": columns, "foreign_keys": foreign_keys,
                "wallet": wallet, "ledger": ledger,
            }


def main():
    require_ci()
    if len(sys.argv) != 2 or sys.argv[1] not in ("prepare", "verify", "guard"):
        raise RuntimeError("EXPECTED_prepare_verify_or_guard")
    mode = sys.argv[1]
    if mode == "guard":
        try:
            inventory(BROKEN)
        except RuntimeError as e:
            if str(e) != "DANGEROUS_V9_HISTORY_MISSING_STOP":
                raise
            print("SHADOW GUARD PASS: missing original V9 history rejected before Spring startup.")
            return
        raise RuntimeError("UNSAFE_SHADOW_WITHOUT_V9_WAS_ACCEPTED")

    if mode == "prepare":
        if MANIFEST.exists():
            raise RuntimeError("SHADOW_MANIFEST_ALREADY_EXISTS")
        data = inventory(SOURCE)
        MANIFEST.write_text(
            json.dumps(data, sort_keys=True, default=str, ensure_ascii=False),
            encoding="utf8"
        )
        print("SHADOW SOURCE PASS: synthetic finance rows, complete Flyway V9/V12 history, "
              f"{len(data['tables'])} tables and {len(data['foreign_keys'])} FKs fingerprinted.")
        return

    if not MANIFEST.exists():
        raise RuntimeError("SOURCE_FINGERPRINT_MISSING")
    expected = json.loads(MANIFEST.read_text(encoding="utf8"))
    actual = inventory(SHADOW)
    # manifest's wallet/ledger decimals are stringified during JSON encoding.
    actual = json.loads(json.dumps(actual, default=str, sort_keys=True))
    if actual != expected:
        affected = [table for table in expected["tables"]
                    if expected["tables"].get(table) != actual["tables"].get(table)]
        raise RuntimeError(
            "SHADOW_MISMATCH: table hashes/schema/FKs/Flyway differs; tables="
            + ",".join(affected[:8])
        )
    print("SHADOW RESTORE PASS: all tables, complete Flyway history, all foreign keys, "
          "member IDs, wallet balances and ledger entries match snapshot.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("SHADOW RESTORE BLOCKED: " + str(error), file=sys.stderr)
        sys.exit(1)
