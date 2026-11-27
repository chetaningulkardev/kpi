"""Point every component's private extractError() at the shared helper.

The method is kept (rather than rewriting ~60 call sites) so the diff stays
reviewable; each body becomes a one-line delegation.
"""
import os
import re

ROOT = "frontend/src/app"

# private extractError(err: any, fallback: string): string {
#     return err?.error?.error || err?.error?.message || err?.message || fallback;
# }
BODY = re.compile(
    r"(private extractError\(\s*err: any,\s*(?P<fb>\w+): string\s*\): string \{)"
    r"\s*return [^;]*?;\s*"
    r"(\})",
    re.S,
)

# str.format() is unusable here: the line contains literal braces.
IMPORT_LINE = "import { extractHttpError } from 'REL';"


def relative_import(from_file: str) -> str:
    target = os.path.join(ROOT, "services", "http-error.util")
    rel = os.path.relpath(target, os.path.dirname(from_file))
    if not rel.startswith("."):
        rel = "./" + rel
    return rel


def main():
    changed = []
    for dirpath, _dirs, files in os.walk(ROOT):
        for name in files:
            if not name.endswith(".ts") or name.endswith(".spec.ts"):
                continue
            path = os.path.join(dirpath, name)
            src = open(path).read()
            if "private extractError(" not in src:
                continue

            new_src, count = BODY.subn(
                lambda m: "%s\n    return extractHttpError(err, %s);\n  %s"
                % (m.group(1), m.group("fb"), m.group(3)),
                src,
            )
            if count == 0:
                print("SKIPPED (unrecognised body): %s" % path)
                continue

            if "extractHttpError" not in src:
                # Insert after the final existing import so ordering stays tidy.
                imports = list(re.finditer(r"^import .*?;\s*$", new_src, re.M))
                insert_at = imports[-1].end()
                new_src = (
                    new_src[:insert_at]
                    + "\n"
                    + IMPORT_LINE.replace("REL", relative_import(path))
                    + new_src[insert_at:]
                )

            open(path, "w").write(new_src)
            changed.append(path)

    print("rewrote %d files" % len(changed))
    for path in changed:
        print("  " + path)


if __name__ == "__main__":
    main()



