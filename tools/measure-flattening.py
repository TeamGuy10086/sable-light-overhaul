"""Count what the raw line number in a driver log implies about Iris' flattening.

Iris hands the driver a preprocessed source whose lines do not match any single file: the log's
"0(NN) : error" numbers index that flattened text. This measures how much has to be dropped
(comments, blank lines, dead #if branches) for a known line of ours to land on the number the
driver reported, which tells us whether the driver's text around it is ours alone or ours inside
pack context.

    python tools/measure-flattening.py <assembled-file> <needle> <driver-line>
"""

import re
import sys


def main():
    if len(sys.argv) != 4:
        print(__doc__)
        return 2
    path, needle, driver_line = sys.argv[1], sys.argv[2], int(sys.argv[3])
    with open(path, encoding="utf-8", errors="replace") as handle:
        lines = handle.read().split("\n")
    index = next(i for i, line in enumerate(lines, 1) if needle in line)
    blank = sum(1 for line in lines[:index] if not line.strip())
    comment = sum(1 for line in lines[:index] if line.strip().startswith(("//", "/*", "*", "*/")))
    code = index - blank - comment
    print("raw line of '{}': {}".format(needle, index))
    print("blank before it: {}  comment-ish: {}  live-ish: {}".format(blank, comment, code))
    print("driver reported: {}  (raw - driver = {})".format(driver_line, index - driver_line))
    print("live-ish - driver = {}".format(code - driver_line))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
