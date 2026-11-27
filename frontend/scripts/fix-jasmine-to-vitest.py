#!/usr/bin/env python3
"""
Migrate Jasmine-specific test APIs to Vitest equivalents across frontend spec files.

The Angular 21 `@angular/build:unit-test` builder runs Vitest, but several spec
files in this repo were written against Jasmine. This script rewrites the
incompatible APIs so the whole suite type-checks and runs.

Usage:
    python3 scripts/fix-jasmine-to-vitest.py [--check]

    --check   Report files that would change without writing them.
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys

SPEC_ROOT = pathlib.Path(__file__).resolve().parent.parent / "src"


def migrate(text: str) -> str:
    """Apply all Jasmine -> Vitest rewrites to a spec file's contents."""

    # ── Boolean matchers ──────────────────────────────────────────────
    text = text.replace(".toBeFalse()", ".toBe(false)")
    text = text.replace(".toBeTrue()", ".toBe(true)")

    # ── Size matcher ──────────────────────────────────────────────────
    text = text.replace(".toHaveSize(", ".toHaveLength(")

    # ── Asymmetric matchers: jasmine.* -> expect.* ────────────────────
    for name in ("any", "objectContaining", "arrayContaining", "stringContaining"):
        text = re.sub(rf"\bjasmine\.{name}\(", f"expect.{name}(", text)

    # ── Spy creation ──────────────────────────────────────────────────
    # jasmine.createSpy() / jasmine.createSpy('name') -> vi.fn()
    text = re.sub(r"\bjasmine\.createSpy\(\s*(?:'[^']*'|\"[^\"]*\")?\s*\)", "vi.fn()", text)

    # ── Spy types ─────────────────────────────────────────────────────
    # jasmine.SpyObj<T> -> any  (must run before the bare jasmine.Spy rule)
    text = re.sub(r"\bjasmine\.SpyObj<[^>]*>", "any", text)
    # jasmine.Spy / jasmine.Spy<...> -> ReturnType<typeof vi.fn>
    text = re.sub(r"\bjasmine\.Spy(?:<[^>]*>)?", "ReturnType<typeof vi.fn>", text)

    # ── spyOn -> vi.spyOn (skip already-qualified vi.spyOn) ───────────
    text = re.sub(r"(?<![.\w])spyOn\(", "vi.spyOn(", text)

    # ── Spy strategies ────────────────────────────────────────────────
    text = text.replace(".and.returnValue(", ".mockReturnValue(")
    text = text.replace(".and.callFake(", ".mockImplementation(")
    text = text.replace(".and.resolveTo(", ".mockResolvedValue(")
    text = text.replace(".and.rejectWith(", ".mockRejectedValue(")
    text = text.replace(".and.callThrough()", "")

    # ── Spy call introspection ────────────────────────────────────────
    text = text.replace(".calls.count()", ".mock.calls.length")
    text = text.replace(".calls.mostRecent().args", ".mock.calls.at(-1)")
    text = text.replace(".calls.reset()", ".mockClear()")
    text = text.replace(".calls.any()", ".mock.calls.length > 0")
    text = re.sub(r"\.calls\.argsFor\((\d+)\)", r".mock.calls[\1]", text)

    return text


def ensure_vi_import(text: str) -> str:
    """Prepend a `vi` import when the file uses `vi.` but doesn't import it."""
    if not re.search(r"\bvi\.", text):
        return text
    if re.search(r"^import\s+\{[^}]*\bvi\b[^}]*\}\s+from\s+['\"]vitest['\"]", text, re.M):
        return text
    return "import { vi } from 'vitest';\n" + text


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="report changes without writing")
    args = parser.parse_args()

    spec_files = sorted(SPEC_ROOT.rglob("*.spec.ts"))
    if not spec_files:
        print(f"No spec files found under {SPEC_ROOT}", file=sys.stderr)
        return 1

    changed: list[pathlib.Path] = []

    for path in spec_files:
        original = path.read_text(encoding="utf-8")
        updated = ensure_vi_import(migrate(original))
        if updated != original:
            changed.append(path)
            if not args.check:
                path.write_text(updated, encoding="utf-8")

    verb = "Would update" if args.check else "Updated"
    for path in changed:
        print(f"{verb}: {path.relative_to(SPEC_ROOT.parent)}")

    print(f"\n{verb} {len(changed)} of {len(spec_files)} spec file(s).")
    return 1 if (args.check and changed) else 0


if __name__ == "__main__":
    raise SystemExit(main())

