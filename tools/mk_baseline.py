"""Generate the Flyway V1 baseline from a pg_dump --schema-only snapshot.

Run once, when Flyway is introduced:

    pg_dump ... --schema-only --no-owner --no-privileges > /tmp/baseline_raw.sql
    python3 tools/mk_baseline.py

Kept in the repo so the baseline can be regenerated and diffed rather than
hand-edited.
"""
import re
import sys

# Tables to exclude from the baseline.
#
# Deliberately empty. It is tempting to drop the `employee`, `kpi`,
# `kpi_template`, `kpi_template_item`, `review_cycle`, `lookup_value` and
# `workflow_*` tables, because they look like leftovers from an earlier schema
# next to the near-identical `users`, `kpis`, `kpi_templates`,
# `kpi_template_items` and `review_cycles`.
#
# They are not leftovers. There is a second, parallel domain model under
# com.example.kpi.model that maps them, and parts of it are live:
# LookupValue backs LookupConfigurationController and Employee backs
# EmployeeConfigurationController. Hibernate validates every @Entity at
# startup, so removing any of these tables fails the boot outright.
#
# Consolidating the two models is a refactor in its own right; until then the
# baseline has to describe the schema as it actually is.
LEGACY: list[str] = []

# The trailing \b is what would keep `public.kpi` from also matching
# `public.kpis`. With LEGACY empty the pattern must never match anything, so
# guard against the empty-alternation regex that would match every statement.
LEGACY_RE = (
    re.compile(r"public\.(?:%s)\b" % "|".join(sorted(LEGACY, key=len, reverse=True)))
    if LEGACY
    else None
)

SRC = "/tmp/baseline_raw.sql"
DST = "src/main/resources/db/migration/V1__baseline_schema.sql"

# psql meta-commands and session GUCs are not valid Flyway migration content.
SKIP_PREFIX = ("SET ", "SELECT pg_catalog.set_config", "CREATE SCHEMA")

HEADER = """--
-- V1 :: Baseline schema for KPI.
--
-- Snapshot of the Hibernate-generated schema taken at the point Flyway was
-- introduced. Existing databases are baselined at this version and therefore
-- SKIP this script entirely; only brand-new databases execute it.
--
-- It describes the schema as it actually is, including the tables belonging to
-- the parallel com.example.kpi.model domain (employee, kpi, kpi_template,
-- kpi_template_item, review_cycle, lookup_value, workflow_*). Those sit
-- confusingly close to users / kpis / kpi_templates / kpi_template_items /
-- review_cycles, but they are still mapped @Entity classes -- LookupValue and
-- Employee back live controllers -- and Hibernate's schema validation fails
-- the boot if any of them is missing.
--
-- From this point on the schema is owned by these migrations, not by
-- Hibernate's ddl-auto.
--

"""



def split_statements(raw):
    """pg_dump terminates every statement with ';' at end of line."""
    statements, buf = [], []
    for line in raw.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("--") or stripped.startswith("\\"):
            continue
        buf.append(line)
        if stripped.endswith(";"):
            statements.append("\n".join(buf))
            buf = []
    if buf:
        statements.append("\n".join(buf))
    return statements


def main():
    with open(SRC) as handle:
        statements = split_statements(handle.read())

    kept, dropped = [], []
    for statement in statements:
        if statement.lstrip().startswith(SKIP_PREFIX):
            continue
        if LEGACY_RE is not None and LEGACY_RE.search(statement):
            dropped.append(statement)
            continue
        kept.append(statement)

    # A foreign key pointing from a live table at an excluded one would be lost
    # silently by the filter above, so make some noise about it instead.
    for statement in dropped:
        if "REFERENCES" not in statement:
            continue
        match = re.search(r"ALTER TABLE ONLY (public\.\w+)", statement)
        if match and not LEGACY_RE.match(match.group(1)):
            print("WARNING live->excluded FK dropped:\n%s" % statement, file=sys.stderr)

    with open(DST, "w") as handle:
        handle.write(HEADER + "\n\n".join(kept) + "\n")

    print("kept=%d dropped=%d -> %s" % (len(kept), len(dropped), DST))


if __name__ == "__main__":
    main()

