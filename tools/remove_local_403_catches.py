"""Remove the hand-rolled SecurityException -> empty 403 catches.

ApiExceptionHandler now maps SecurityException to 403 globally *and* includes
the service's message ("You are not allowed to review this KPI"). These local
catches predate that, and returned ResponseEntity.status(403).build() - a 403
with an empty body, so the user was told nothing about why.

Rewrites:

        try {
            <body>
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }

into <body>, dedented one level.
"""
import re

PATH = "src/main/java/com/example/kpi/controller/KpiReviewController.java"

PATTERN = re.compile(
    r"[ \t]*try \{\n"
    r"(?P<body>.*?)\n"
    r"[ \t]*\} catch \(SecurityException \w+\) \{\n"
    r"[ \t]*return ResponseEntity\.status\(403\)\.build\(\);\n"
    r"[ \t]*\}\n",
    re.S,
)


def dedent(body: str) -> str:
    out = []
    for line in body.split("\n"):
        out.append(line[4:] if line.startswith("    ") else line)
    return "\n".join(out)


def main():
    src = open(PATH).read()
    new_src, count = PATTERN.subn(lambda m: dedent(m.group("body")) + "\n", src)
    open(PATH, "w").write(new_src)
    print("removed %d local SecurityException catches" % count)


if __name__ == "__main__":
    main()

