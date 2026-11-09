import re, sys
import xml.etree.ElementTree as ET

needle = sys.argv[1]
exact = needle.startswith("=")
needle = needle.lstrip("=").lower()
root = ET.parse("ui.xml").getroot()
for n in root.iter("node"):
    texts = [(n.get("text") or "").lower(), (n.get("content-desc") or "").lower()]
    ok = any(t == needle for t in texts) if exact else any(needle in t for t in texts)
    if ok and n.get("bounds"):
        l, t, r, b = map(int, re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds")).groups())
        print((l + r) // 2, (t + b) // 2)
        sys.exit(0)
sys.exit(1)
