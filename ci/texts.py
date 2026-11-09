import xml.etree.ElementTree as ET

root = ET.parse("ui.xml").getroot()
seen = []
for n in root.iter("node"):
    for v in (n.get("text"), n.get("content-desc")):
        if v and v.strip() and v not in seen:
            seen.append(v)
print(" | ".join(seen))
