#!/usr/bin/env python3
import re

# Đọc file
with open('app/src/main/res/values/strings.xml', 'r', encoding='utf-8') as f:
    content = f.read()

lines = content.split('\n')
seen_keys = set()
output_lines = []

for line in lines:
    # Tìm name attribute trong <string> và <plurals> tags
    match = re.search(r'<(?:string|plurals)\s+[^>]*name="([^"]+)"', line)

    if match:
        key = match.group(1)
        if key not in seen_keys:
            seen_keys.add(key)
            output_lines.append(line)
        # else: skip duplicate
    else:
        # Không phải dòng <string> hay <plurals>, giữ lại
        output_lines.append(line)

# Ghi lại file
with open('app/src/main/res/values/strings.xml', 'w', encoding='utf-8') as f:
    f.write('\n'.join(output_lines))

print(f"✓ Done! Removed duplicates from strings.xml")
print(f"Total unique keys: {len(seen_keys)}")
print(f"Total lines kept: {len(output_lines)}")

