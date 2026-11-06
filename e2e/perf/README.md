# Query performance checks

Both scripts need a running backend and a Postgres with `pg_stat_statements`
(`postgres -c shared_preload_libraries=pg_stat_statements`). They talk to Postgres through
`docker exec`; set `GITNODE_DB_CONTAINER` (default `gitnode-access-validation`), `PGUSER`
(`postgres`), `PGDATABASE` (`access_test`) and `GITNODE_API_BASE_URL` (`http://localhost:8080`).

- `python3 perf/query_counts.py` builds a small data set (30 repos, 30 issues, 12 pull requests,
  collaborators, tags), then calls each list endpoint with page size 3 and 30 and prints the number
  of SQL statements. A count that grows with the page size is an N+1 query.
- `python3 perf/missing_indexes.py` replays the heaviest statements with sequential scans disabled
  and lists those that still need a sequential scan (a missing index), plus foreign keys without an
  index on the referencing column.
