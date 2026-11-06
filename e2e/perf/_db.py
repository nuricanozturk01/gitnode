"""Shared helpers for the performance checks."""

import json
import os
import subprocess
import urllib.error
import urllib.request

API = os.environ.get("GITNODE_API_BASE_URL", "http://localhost:8080")
CONTAINER = os.environ.get("GITNODE_DB_CONTAINER", "gitnode-access-validation")
PGUSER = os.environ.get("PGUSER", "postgres")
PGDATABASE = os.environ.get("PGDATABASE", "access_test")


def sql(statement: str) -> str:
    out = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "psql", "-U", PGUSER, "-d", PGDATABASE, "-At", "-c", statement],
        capture_output=True,
        text=True,
        check=False,
    )
    return out.stdout.strip()


def call(method: str, path: str, token: str | None = None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, method=method, data=data)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as resp:
            text = resp.read().decode()
            return resp.status, (json.loads(text) if text else None)
    except urllib.error.HTTPError as err:
        return err.code, err.read().decode()[:200]
