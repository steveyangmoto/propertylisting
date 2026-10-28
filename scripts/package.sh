#!/usr/bin/env bash
# Package the project into a zip, with README.pdf generated from README.md.

source "$(dirname "$0")/lib/common.sh"

usage() {
    cat <<'EOF'
Package the project into dist/PropertySearch-<date>.zip, with README.pdf generated from README.md.

  scripts/package.sh             Build the zip
  scripts/package.sh --no-pdf    Build the zip without README.pdf

Files are selected with git, so anything in .gitignore (build output, .gradle, local.properties)
is left out. Generating the PDF needs Node.js (for npx) and Google Chrome or Chromium.
EOF
}

NAME="PropertySearch"
WITH_PDF=1
MARKED_VERSION="16.4.1"

case "${1:-}" in
    "") ;;
    --no-pdf) WITH_PDF=0 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1 (see scripts/package.sh --help)" >&2; exit 1 ;;
esac

find_chrome() {
    local candidate
    for candidate in \
        "${CHROME:-}" \
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
        "/Applications/Chromium.app/Contents/MacOS/Chromium" \
        "$(command -v google-chrome 2>/dev/null)" \
        "$(command -v chromium 2>/dev/null)" \
        "$(command -v chromium-browser 2>/dev/null)"; do
        [[ -n "$candidate" && -x "$candidate" ]] && { echo "$candidate"; return; }
    done
}

build_pdf() {
    local out="$1" html="$2"
    command -v npx > /dev/null || fail "npx (Node.js) is required to render README.pdf. Use --no-pdf to skip it."
    local chrome
    chrome="$(find_chrome)"
    [[ -n "$chrome" ]] || fail "Google Chrome or Chromium is required to render README.pdf. Set CHROME=/path/to/chrome or use --no-pdf."

    info "Rendering README.md to HTML"
    {
        cat <<'HTML'
<!doctype html>
<html><head><meta charset="utf-8"><title>Property Search</title>
<style>
  @page { margin: 18mm 16mm; }
  body { font: 11pt/1.5 -apple-system, "Helvetica Neue", Arial, sans-serif; color: #1f2328; }
  h1, h2, h3 { line-height: 1.25; margin: 1.4em 0 0.5em; }
  h1 { font-size: 22pt; border-bottom: 1px solid #d0d7de; padding-bottom: 0.3em; margin-top: 0; }
  h2 { font-size: 16pt; border-bottom: 1px solid #d0d7de; padding-bottom: 0.25em; }
  h3 { font-size: 13pt; }
  h2, h3 { break-after: avoid; }
  code { font: 9.5pt Menlo, Consolas, monospace; background: #f3f4f6; padding: 0.1em 0.3em; border-radius: 4px; }
  pre { background: #f6f8fa; padding: 10px 12px; border-radius: 6px; overflow-x: auto; break-inside: avoid; }
  pre code { background: none; padding: 0; }
  table { border-collapse: collapse; margin: 0.8em 0; width: 100%; break-inside: avoid; }
  th, td { border: 1px solid #d0d7de; padding: 5px 9px; text-align: left; vertical-align: top; }
  th { background: #f6f8fa; }
  a { color: #0969da; text-decoration: none; }
</style></head><body>
HTML
        NPM_CONFIG_UPDATE_NOTIFIER=false npx --yes --loglevel=error "marked@$MARKED_VERSION" --gfm < README.md
        echo '</body></html>'
    } > "$html"

    info "Printing README.pdf with $(basename "$chrome")"
    "$chrome" --headless --disable-gpu --no-pdf-header-footer --print-to-pdf-no-header \
        --print-to-pdf="$out" "file://$html" > /dev/null 2>&1
    [[ -s "$out" ]] || fail "Chrome didn't produce a PDF."
}

command -v git > /dev/null || fail "git is required to select the files to package."
command -v zip > /dev/null || fail "zip is required."

STAGING="$(mktemp -d)"
trap 'rm -rf "$STAGING"' EXIT
ROOT="$STAGING/$NAME"
mkdir -p "$ROOT"

info "Collecting project files"
# Tracked plus untracked-but-not-ignored files, so this works before the first commit too.
git ls-files --cached --others --exclude-standard -z \
    | while IFS= read -r -d '' file; do
        [[ -f "$file" ]] || continue
        case "$file" in dist/*) continue ;; esac
        mkdir -p "$ROOT/$(dirname "$file")"
        cp -p "$file" "$ROOT/$file"
    done

if [[ "$WITH_PDF" == 1 ]]; then
    build_pdf "$ROOT/README.pdf" "$STAGING/README.html"
fi

mkdir -p dist
ZIP="$PWD/dist/$NAME-$(date +%Y%m%d).zip"
rm -f "$ZIP"
info "Creating $(basename "$ZIP")"
(cd "$STAGING" && zip -qr -X "$ZIP" "$NAME")

info "Done: dist/$(basename "$ZIP") ($(du -h "$ZIP" | cut -f1 | tr -d ' '), $(zipinfo -1 "$ZIP" | grep -vc '/$') files)"
