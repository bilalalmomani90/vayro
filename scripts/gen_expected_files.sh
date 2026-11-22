#!/usr/bin/env bash
# يولّد scripts/expected_files.txt (بصمة sha256 لكل ملف مصدر). يُشغَّل قبل تغليف كل إصدار.
set -euo pipefail
cd "$(dirname "$0")/.."
find app scripts gradle.properties settings.gradle.kts build.gradle.kts .gitignore CHANGELOG.md ROADMAP.md -type f \
  ! -path '*/build/*' ! -path '*/.gradle/*' \
  ! -name expected_files.txt ! -name PROJECT_INFO.txt | LC_ALL=C sort | while read -r f; do
  printf '%s  %s\n' "$(sha256sum "$f" | cut -d' ' -f1)" "$f"
done > scripts/expected_files.txt
echo "expected_files.txt: $(wc -l < scripts/expected_files.txt) ملفاً"
