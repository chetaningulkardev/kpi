"""Report catch blocks that swallow an exception without logging or rethrowing.

A catch that neither logs nor rethrows makes a failure invisible: the request
returns success, nothing appears in the log, and the cause is unrecoverable
after the fact.

Heuristic, not a parser - review each hit by hand.
"""
import os
import re

ROOT = "src/main/java"

CATCH = re.compile(r"catch\s*\((?P<type>[A-Za-z.]*(?:Exception|Throwable|Error))\s+(?P<var>\w+)\s*\)\s*\{")

SIGNALS = ("log.", "logger.", "throw ", "LOG.", "System.err")


def block_after(src: str, open_brace_index: int) -> str:
    depth = 0
    for i in range(open_brace_index, len(src)):
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
            if depth == 0:
                return src[open_brace_index + 1:i]
    return ""


def main():
    silent = []
    total = 0
    for dirpath, _dirs, files in os.walk(ROOT):
        for name in files:
            if not name.endswith(".java"):
                continue
            path = os.path.join(dirpath, name)
            src = open(path).read()
            for match in CATCH.finditer(src):
                total += 1
                body = block_after(src, match.end() - 1)
                if any(signal in body for signal in SIGNALS):
                    continue
                line = src[:match.start()].count("\n") + 1
                summary = " ".join(body.split())[:90]
                silent.append((path, line, match.group("type"), summary))

    print("catch blocks scanned: %d" % total)
    print("silent (no log, no rethrow): %d\n" % len(silent))
    for path, line, exc_type, summary in silent:
        print("%s:%d  catch (%s)" % (path.replace(ROOT + "/com/example/kpi/", ""), line, exc_type))
        print("      { %s }" % (summary or "EMPTY"))


if __name__ == "__main__":
    main()

