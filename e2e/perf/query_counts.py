"""Counts SQL statements per list endpoint at two page sizes to spot N+1 queries."""

from _db import call, sql

SELF_QUERIES = "query not ilike '%pg_stat_statements%' and query not ilike '%pg_catalog%'"


def register(name: str) -> str:
    _, body = call(
        "POST",
        "/api/auth/register",
        None,
        {"username": name, "email": f"{name}@example.com", "password": "Demo12345!"},
    )
    return body["token"]


def seed() -> str:
    sql("create extension if not exists pg_stat_statements")
    token = register("perfmain")
    others = [register(f"perfo{i}") for i in range(8)]
    for i in range(30):
        call("POST", "/api/repo", token, {"name": f"pr{i}", "description": "d", "isPrivate": False})
    repo = "perfmain/pr0"
    for i in range(30):
        call("POST", f"/api/repos/{repo}/issues", token, {"title": f"issue {i}", "description": "d"})
    for i in range(12):
        call("POST", f"/api/repos/{repo}/branches", token, {"name": f"b{i}", "sourceBranch": "main"})
        call("PUT", f"/api/repos/{repo}/blob/b{i}/f{i}.txt", token, {"content": f"c{i}\n", "commitMessage": f"f{i}"})
        call("POST", f"/api/repos/{repo}/pulls", token,
             {"title": f"pr {i}", "description": "d", "sourceBranch": f"b{i}", "targetBranch": "main", "isDraft": False})
    for i in range(len(others)):
        call("POST", f"/api/repos/{repo}/collaborators", token, {"username": f"perfo{i}", "permissions": ["READ"]})
    for i in range(10):
        call("POST", f"/api/repos/{repo}/tags", token, {"name": f"t{i}", "targetCommitish": "main"})
    return token


def count(path: str, token: str) -> tuple[int, int]:
    call("GET", path, token)  # warm up caches and classes
    sql("select pg_stat_statements_reset()")
    status, _ = call("GET", path, token)
    total = sql(f"select coalesce(sum(calls),0) from pg_stat_statements where {SELF_QUERIES}")
    return status, int(float(total or 0))


def main() -> None:
    token = seed()
    repo = "/api/repos/perfmain/pr0"
    cases = [
        ("repo list", "/api/repo/perfmain?size={n}&page=0"),
        ("issues", repo + "/issues?size={n}&page=0"),
        ("pull requests", repo + "/pulls?size={n}&page=0"),
        ("branches", repo + "/branches?size={n}"),
        ("tags", repo + "/tags?size={n}"),
        ("collaborators", repo + "/collaborators?size={n}&page=0"),
        ("notifications", "/api/notifications?size={n}&page=0"),
        ("commits", repo + "/commits?branch=main&size={n}&page=0"),
        ("runs", repo + "/actions/runs?size={n}&page=0"),
    ]
    print(f"{'endpoint':16} {'size=3':>10} {'size=30':>10}")
    for name, template in cases:
        small = count(template.format(n=3), token)
        large = count(template.format(n=30), token)
        flag = "  <-- grows with page size (N+1)" if large[1] > small[1] + 1 else ""
        print(f"{name:16} {small[0]}/{small[1]:>6} {large[0]}/{large[1]:>6}{flag}")


if __name__ == "__main__":
    main()
