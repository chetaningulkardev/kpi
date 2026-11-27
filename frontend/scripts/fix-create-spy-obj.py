#!/usr/bin/env python3
"""
Replace Jasmine's `createSpyObj` with the project's Vitest helper.

Rewrites:
    jasmine.createSpyObj<T>('Name', ['a', 'b'])
into:
    createSpyObj<T>(['a', 'b'])

and inserts the matching `import { createSpyObj } from '.../testing/spy-utils';`.

Usage:
    python3 scripts/fix-create-spy-obj.py [--check]
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys

FRONTEND_ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC_ROOT = FRONTEND_ROOT / "src"
HELPER_MODULE = SRC_ROOT / "testing" / "spy-utils.ts"

CREATE_SPY_OBJ = re.compile(
    r"jasmine\.createSpyObj(?P<generic><[^>]*>)?\(\s*(?:'[^']*'|\"[^\"]*\")\s*,\s*"
)


def relative_import(spec_file: pathlib.Path) -> str:
    """Build a POSIX-style relative import path from a spec file to the helper."""
    import os

    rel = os.path.relpath(HELPER_MODULE.with_suffix(""), start=spec_file.parent)
    rel = pathlib.PurePath(rel).as_posix()
    return rel if rel.startswith(".") else f"./{rel}"


def insert_import(text: str, import_path: str) -> str:
    """Insert the helper import after the final existing top-level import."""
    if "createSpyObj" not in text:
        return text
    if re.search(r"import\s+\{[^}]*\bcreateSpyObj\b[^}]*\}", text):
        return text

    lines = text.split("\n")
    import_lines = [i for i, line in enumerate(lines) if line.startswith("import ")]
    insert_at = (import_lines[-1] + 1) if import_lines else 0
    lines.insert(insert_at, f"import {{ createSpyObj }} from '{import_path}';")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="report changes without writing")
    args = parser.parse_args()

    if not HELPER_MODULE.exists():
        print(f"Missing helper module: {HELPER_MODULE}", file=sys.stderr)
        return 1

    changed: list[pathlib.Path] = []

    for spec_file in sorted(SRC_ROOT.rglob("*.spec.ts")):
        original = spec_file.read_text(encoding="utf-8")
        if "jasmine.createSpyObj" not in original:
            continue

        updated = CREATE_SPY_OBJ.sub(
            lambda m: f"createSpyObj{m.group('generic') or ''}(", original
        )
        updated = insert_import(updated, relative_import(spec_file))

        if updated != original:
            changed.append(spec_file)
            if not args.check:
                spec_file.write_text(updated, encoding="utf-8")

    verb = "Would update" if args.check else "Updated"
    for path in changed:
        print(f"{verb}: {path.relative_to(FRONTEND_ROOT)}")
    print(f"\n{verb} {len(changed)} spec file(s).")

    return 1 if (args.check and changed) else 0


if __name__ == "__main__":
    raise SystemExit(main())

