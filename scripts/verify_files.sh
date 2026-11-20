#!/usr/bin/env bash
# يتحقق أن المستودع يحتوي كل ملفات المشروع قبل بدء البناء.
# مفقود = فشل واضح بقائمة الملفات. مختلف المحتوى = تحذير فقط (قد تكون نسخة قديمة أو تعديلك المقصود).
set -uo pipefail
cd "$(dirname "$0")/.."

MANIFEST="scripts/expected_files.txt"
if [ ! -f "$MANIFEST" ]; then
  echo "::error::scripts/expected_files.txt غير موجود: الرفع ناقص. ارفع المشروع كاملاً من آخر ملف zip."
  exit 1
fi

missing=()
different=()
total=0
while read -r hash path; do
  [ -z "${path:-}" ] && continue
  total=$((total + 1))
  if [ ! -f "$path" ]; then
    missing+=("$path")
  elif [ "$(sha256sum "$path" | cut -d' ' -f1)" != "$hash" ]; then
    different+=("$path")
  fi
done < "$MANIFEST"

echo "تم فحص $total ملفاً: مفقود=${#missing[@]} ، مختلف=${#different[@]}"

if [ ${#different[@]} -gt 0 ]; then
  echo "::warning::ملفات محتواها يختلف عن النسخة المتوقعة (نسخة قديمة أو تعديل منك):"
  printf '  %s\n' "${different[@]}"
fi

if [ ${#missing[@]} -gt 0 ]; then
  echo "::error::ملفات مفقودة في المستودع (${#missing[@]}):"
  printf '  %s\n' "${missing[@]}"
  echo "ارفع هذه الملفات (أو المشروع كاملاً) ثم أعد التشغيل. السبب الشائع: الرفع عبر المتصفح يتجاوز بعض الملفات عند رفع عدد كبير دفعة واحدة."
  exit 1
fi
echo "كل الملفات موجودة."
