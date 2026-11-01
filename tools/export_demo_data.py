#!/usr/bin/env python3
"""Dev-time helper (NOT needed to run the project): turns the INSERT statements in sql/*.sql
into tab-separated files in data/demo/, which the Java 'demo mode' DAO reads when MySQL is not available."""
import re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "data" / "demo"
OUT.mkdir(parents=True, exist_ok=True)

INSERT = re.compile(r"INSERT\s+(?:IGNORE\s+)?INTO\s+(\w+)\s*\(([^)]*)\)\s*VALUES", re.I)

def tuples(text, pos):
    """Yield value lists from '(..),(..),...;' starting at pos. Handles 'it''s' quotes."""
    n = len(text)
    while pos < n:
        while pos < n and text[pos] in " \r\n,":
            pos += 1
        if pos >= n or text[pos] == ";":
            return
        assert text[pos] == "(", text[pos:pos + 40]
        pos += 1
        vals, cur, inq, isq = [], [], False, False
        while True:
            c = text[pos]
            if inq:
                if c == "'" and text[pos + 1:pos + 2] == "'":
                    cur.append("'"); pos += 2; continue
                if c == "'":
                    inq = False
                else:
                    cur.append(c)
            elif c == "'":
                inq = isq = True
            elif c == "," or c == ")":
                s = "".join(cur).strip()
                vals.append(s if isq else ("\\N" if s.upper() == "NULL" else s))
                cur, isq = [], False
                if c == ")":
                    pos += 1
                    break
            else:
                cur.append(c)
            pos += 1
        yield vals

tables = {}
for f in sorted((ROOT / "sql").glob("*.sql")):
    text = f.read_text(encoding="utf-8")
    for m in INSERT.finditer(text):
        t, cols = m.group(1), [c.strip() for c in m.group(2).split(",")]
        rows = tables.setdefault(t, (cols, []))[1]
        rows.extend(tuples(text, m.end()))

for t, (cols, rows) in tables.items():
    seen, lines = set(), ["\t".join(cols)]
    for r in rows:
        assert len(r) == len(cols), (t, r)
        key = r[0] if t != "fleet_route_stops" else (r[0], r[1])
        if t in ("pickup_stops", "fleet_routes", "fleet_buses", "stop_coordinates", "fleet_route_stops") and key in seen:
            continue  # INSERT IGNORE semantics
        seen.add(key)
        lines.append("\t".join(r))
    (OUT / f"{t}.tsv").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{t}: {len(lines) - 1} rows")
