#!/usr/bin/env python3
"""Runs the compat corpus against a live GitNode backend with a registered runner.

usage: run.py <base-url> <access-token> <owner> [workflow-name ...]

Creates the repo `compat` (if missing), commits the workflows and the local action, dispatches
each workflow and waits for it. Exit status 1 when a workflow does not end in `success`.
Set VERBOSE=1 to print every step's log.
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

BASE, TOKEN, OWNER = sys.argv[1].rstrip("/"), sys.argv[2], sys.argv[3]
ONLY = set(sys.argv[4:])
HERE = os.path.dirname(os.path.abspath(__file__))
REPO = "compat"
VERBOSE = os.environ.get("VERBOSE") == "1"


def call(method, path, body=None, raw=False, timeout=30):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Authorization", "Bearer " + TOKEN)
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, data=data, timeout=timeout) as resp:
            payload = resp.read()
            return resp.status, (payload.decode() if raw else (json.loads(payload) if payload else None))
    except urllib.error.HTTPError as err:
        return err.code, err.read().decode()


def ensure_repo():
    status, _ = call("GET", f"/api/repos/{OWNER}/{REPO}")
    if status == 200:
        return
    status, body = call("POST", "/api/repo", {
        "name": REPO, "description": "compat corpus", "isPrivate": False, "initializeWithReadme": True})
    if status >= 300:
        sys.exit(f"cannot create repo: {status} {body}")


def put_file(path, content, message):
    for _ in range(10):  # a fresh repo needs a moment before its main branch exists
        status, body = call("PUT", f"/api/repos/{OWNER}/{REPO}/blob/main/{path}",
                            {"content": content, "commitMessage": message})
        if status < 300 or "exist" in str(body).lower():
            return
        if "branchNotFound" not in str(body):
            break
        time.sleep(1)
    print(f"  upload {path}: {status} {str(body)[:120]}")


def upload():
    put_file("README.md", "# compat\n", "compat: readme")
    for name in sorted(os.listdir(os.path.join(HERE, "workflows"))):
        with open(os.path.join(HERE, "workflows", name)) as f:
            put_file(f".gitnode/workflows/{name}", f.read(), f"compat: {name}")
    with open(os.path.join(HERE, "actions", "hello", "action.yml")) as f:
        put_file(".gitnode/actions/hello/action.yml", f.read(), "compat: local action")


def step_logs(step_id):
    status, text = call("GET", f"/api/actions/steps/{step_id}/logs", raw=True, timeout=8)
    lines = []
    for chunk in str(text).split("\n\n"):
        for line in chunk.split("\n"):
            if line.startswith("data:"):
                try:
                    lines.append(json.loads(line[5:].strip()))
                except ValueError:
                    pass
    return lines


def run_workflow(file_name):
    path = f".gitnode/workflows/{file_name}"
    before = {r["id"] for r in call("GET", f"/api/repos/{OWNER}/{REPO}/actions/runs?size=100")[1]["content"]}
    status, body = call("POST", f"/api/repos/{OWNER}/{REPO}/actions/workflows/dispatches",
                        {"filePath": path, "ref": "main"})
    if status >= 300:
        return None, f"dispatch failed: {status} {str(body)[:200]}"
    run_id = None
    for _ in range(30):
        runs = call("GET", f"/api/repos/{OWNER}/{REPO}/actions/runs?size=100")[1]["content"]
        new = [r for r in runs if r["id"] not in before]
        if new:
            run_id = new[0]["id"]
            break
        time.sleep(1)
    if not run_id:
        return None, "no run was created"
    for _ in range(180):
        run = call("GET", f"/api/repos/{OWNER}/{REPO}/actions/runs/{run_id}")[1]
        if run["status"] not in ("queued", "in_progress"):
            return run, None
        time.sleep(1)
    return call("GET", f"/api/repos/{OWNER}/{REPO}/actions/runs/{run_id}")[1], "timed out"


def main():
    ensure_repo()
    upload()
    time.sleep(2)
    failed = []
    for name in sorted(os.listdir(os.path.join(HERE, "workflows"))):
        wf = name.rsplit(".", 1)[0]
        if ONLY and wf not in ONLY:
            continue
        run, err = run_workflow(name)
        if run is None:
            print(f"FAIL {wf}: {err}")
            failed.append(wf)
            continue
        ok = run["conclusion"] == "success" and not err
        print(f"{'ok  ' if ok else 'FAIL'} {wf}: {run['status']}/{run['conclusion']} {err or ''}")
        if not ok:
            failed.append(wf)
        for job in run["jobs"]:
            print(f"     job {job['name']}: {job['status']}/{job['conclusion']}")
            for step in job["steps"]:
                bad = step["conclusion"] not in ("success", "skipped")
                if bad or VERBOSE:
                    print(f"       step {step['stepNumber']} {step['name'] or step['uses'] or ''}: {step['conclusion']}")
                    for line in step_logs(step["id"]):
                        print(f"         [{line['level']}] {line['content'][:200]}")
    print("\nfailed:", ", ".join(failed) if failed else "none")
    sys.exit(1 if failed else 0)


main()
