#!/usr/bin/env python3
"""
Finish migrating Angular specs from Jasmine/Karma idioms to Vitest.

The `@angular/build:unit-test` builder runs Vitest, which does not provide the
Jasmine features several specs still rely on:

1. `fakeAsync()` / `tick()` — Vitest does not run specs inside a `ProxyZone`, so
   these throw "Expected to be running in 'ProxyZone'". Every service stub in
   this repo returns a synchronous `of(...)`, so the wrappers are unnecessary.

2. `tick()` / `flush()` helpers, which only make sense inside `fakeAsync`.

Unlike a naive regex pass, this script locates the *matching* closing paren for
each `fakeAsync(` call by scanning the source while tracking nesting depth and
skipping strings, template literals, and comments. That makes the unwrapping
safe even when the callback body contains nested closures such as
`subscribe(() => { ... })`.

Usage:
    python3 scripts/fix-vitest-specs.py [--check] [files...]
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys

FRONTEND_ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC_ROOT = FRONTEND_ROOT / "src"

TIMER_HELPERS = ("tick", "flush", "flushMicrotasks", "discardPeriodicTasks")


def find_matching_paren(text: str, open_index: int) -> int:
    """
    Return the index of the ')' that matches the '(' at `open_index`.

    Skips string literals, template literals, and both comment styles so that
    parens inside them never affect the depth count.
    """
    if text[open_index] != "(":
        raise ValueError("open_index must point at '('")

    depth = 0
    i = open_index
    n = len(text)

    while i < n:
        ch = text[i]

        # ── Comments ─────────────────────────────────────────────
        if ch == "/" and i + 1 < n:
            nxt = text[i + 1]
            if nxt == "/":
                newline = text.find("\n", i)
                i = n if newline == -1 else newline + 1
                continue
            if nxt == "*":
                end = text.find("*/", i + 2)
                i = n if end == -1 else end + 2
                continue

        # ── String / template literals ───────────────────────────
        if ch in ("'", '"', "`"):
            quote = ch
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == quote:
                    i += 1
                    break
                i += 1
            continue

        # ── Parens ───────────────────────────────────────────────
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                return i

        i += 1

    return -1


def unwrap_fake_async(text: str) -> str:
    """Replace every `fakeAsync(<callback>)` with just `<callback>`."""
    pattern = re.compile(r"\bfakeAsync\s*\(")
    search_from = 0

    while True:
        match = pattern.search(text, search_from)
        if not match:
            break

        open_paren = match.end() - 1
        close_paren = find_matching_paren(text, open_paren)
        if close_paren == -1:
            print("  ! unbalanced parens after fakeAsync(, skipping", file=sys.stderr)
            search_from = match.end()
            continue

        callback = text[open_paren + 1 : close_paren]
        text = text[: match.start()] + callback + text[close_paren + 1 :]
        search_from = match.start()

    return text


def strip_timer_helpers(text: str) -> str:
    """Remove `tick();` / `flush();` style calls that only exist for fakeAsync."""
    names = "|".join(TIMER_HELPERS)
    # Whole-line calls: drop the entire line to keep indentation tidy.
    text = re.sub(rf"^[ \t]*(?:{names})\([^()]*\);[ \t]*\r?\n", "", text, flags=re.M)
    # Any stragglers sharing a line with other statements.
    text = re.sub(rf"\b(?:{names})\([^()]*\);[ \t]*", "", text)
    return text


def prune_testing_imports(text: str) -> str:
    """Drop `fakeAsync` / `tick` / `flush` from `@angular/core/testing` imports."""
    removed = set(TIMER_HELPERS) | {"fakeAsync"}

    def rewrite(match: re.Match[str]) -> str:
        names = [n.strip() for n in match.group(1).split(",") if n.strip()]
        kept = [n for n in names if n not in removed]
        if not kept:
            return ""
        return f"import {{ {', '.join(kept)} }} from '@angular/core/testing';"

    text = re.sub(
        r"import\s*\{([^}]*)\}\s*from\s*'@angular/core/testing';",
        rewrite,
        text,
    )
    # Collapse the blank line left behind when an import was removed entirely.
    return re.sub(r"\n{3,}", "\n\n", text)


def migrate(text: str) -> str:
    text = unwrap_fake_async(text)
    text = strip_timer_helpers(text)
    text = prune_testing_imports(text)
    return text


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="report changes without writing")
    parser.add_argument("files", nargs="*", help="specific spec files (default: all)")
    args = parser.parse_args()

    if args.files:
        spec_files = [pathlib.Path(f) for f in args.files]
    else:
        spec_files = sorted(SRC_ROOT.rglob("*.spec.ts"))

    changed: list[pathlib.Path] = []

    for spec_file in spec_files:
        if not spec_file.exists():
            print(f"skip (missing): {spec_file}", file=sys.stderr)
            continue

        original = spec_file.read_text(encoding="utf-8")
        updated = migrate(original)

        if updated != original:
            changed.append(spec_file)
            if not args.check:
                spec_file.write_text(updated, encoding="utf-8")

    verb = "Would update" if args.check else "Updated"
    for path in changed:
        try:
            rel = path.relative_to(FRONTEND_ROOT)
        except ValueError:
            rel = path
        print(f"{verb}: {rel}")
    print(f"\n{verb} {len(changed)} of {len(spec_files)} spec file(s).")

    return 1 if (args.check and changed) else 0


if __name__ == "__main__":
    raise SystemExit(main())

