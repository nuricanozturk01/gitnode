"""Lists heavy statements that cannot use an index, and foreign keys without an index."""

import re

from _db import sql

SEP = "\x02"


def statements() -> list[str]:
    rows = sql(
        "select queryid||E'\\x02'||calls||E'\\x02'||round(total_exec_time::numeric,0)||E'\\x02'||"
        "replace(query,E'\\n',' ') from pg_stat_statements where calls>=3 and query ~* '^(select|update|delete)' "
        "and query not ilike '%pg_stat%' and query not ilike '%pg_catalog%' "
        "and query not ilike '%information_schema%' order by total_exec_time desc limit 150"
    )
    return rows.split("\n") if rows else []


def main() -> None:
    needing_seq_scan = []
    for row in statements():
        parts = row.split(SEP, 3)
        if len(parts) != 4 or "$" not in parts[3]:
            continue
        _, calls, total, query = parts
        plan = sql("set enable_seqscan=off; explain (generic_plan) " + query)
        if "Seq Scan" in plan:
            tables = re.findall(r"Seq Scan on (\w+)", plan)
            needing_seq_scan.append((int(float(total)), calls, tables, query[:200]))
    print("Statements that still need a sequential scan with seqscan disabled:")
    for total, calls, tables, query in needing_seq_scan:
        print(f"  {total} ms x{calls} on {tables}: {query}")
    if not needing_seq_scan:
        print("  none")
    print("\nForeign keys without an index on the referencing column:")
    fks = sql(
        "select c.conrelid::regclass::text||'.'||a.attname||' -> '||c.confrelid::regclass::text "
        "from pg_constraint c join pg_attribute a on a.attrelid=c.conrelid and a.attnum=c.conkey[1] "
        "where c.contype='f' and array_length(c.conkey,1)=1 and not exists "
        "(select 1 from pg_index i where i.indrelid=c.conrelid and i.indkey[0]=c.conkey[1]) order by 1"
    )
    print(fks if fks else "  none")


if __name__ == "__main__":
    main()
