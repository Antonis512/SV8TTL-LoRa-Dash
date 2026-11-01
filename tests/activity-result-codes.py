"""Prevent picker results being consumed by an unrelated activity-result handler."""
from pathlib import Path
import re

source = (Path(__file__).resolve().parents[1] / "src/main/java/MainActivity.java").read_text(encoding="utf-8")
constants = {}
for declaration in re.findall(r"private static final int\s+([^;]+);", source):
    for name, value in re.findall(r"\b([A-Z][A-Z_]+)\s*=\s*(0x[0-9a-fA-F]+|\d+)", declaration):
        constants[name] = int(value, 0)

handler = source.split("@Override protected void onActivityResult", 1)[1]
handler = handler.split("private void transferHistory", 1)[0]
requests = set(re.findall(r"requestCode\s*==\s*([A-Z][A-Z_]+)", handler))
assert "IMPORT_MAP_PACK" in requests and "ENABLE_BLUETOOTH" in requests
seen = {}
for name in sorted(requests):
    value = constants[name]
    assert value not in seen, f"Activity result {value} is shared by {seen.get(value)} and {name}"
    seen[value] = name
print(f"Activity result request codes are unique ({len(requests)} handlers)")
