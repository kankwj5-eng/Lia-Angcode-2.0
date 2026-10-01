#!/usr/bin/env bash
set -euo pipefail

TERMUX_PACKAGES_COMMIT="${TERMUX_PACKAGES_COMMIT:-2d31765cdab30bbf92f87c495bef6b963df168e5}"
ARCH="${ARCH:-aarch64}"
PROFILE="${PROFILE:-minimal}"
WORKDIR="${WORKDIR:-$PWD/.angcode-runtime-build}"

ANGCODE_APP_PACKAGE="com.kankwj.angcode"
ANGCODE_APP_NAME="AngCode"
ANGCODE_DATA_DIR="/data/data/${ANGCODE_APP_PACKAGE}"

case "$ARCH" in
  aarch64|arm|i686|x86_64) ;;
  *) echo "Arquitectura no soportada: $ARCH" >&2; exit 2 ;;
esac

case "$PROFILE" in
  minimal)
    EXTRA_PACKAGES="proot,proot-distro,git,curl,openssh"
    ;;
  dev)
    EXTRA_PACKAGES="proot,proot-distro,git,curl,openssh,python,nodejs-lts,clang,cmake,ninja,make,sqlite,jq"
    ;;
  full)
    EXTRA_PACKAGES="proot,proot-distro,git,curl,openssh,python,nodejs-lts,clang,cmake,ninja,make,sqlite,jq,ffmpeg,imagemagick,tree-sitter"
    ;;
  *)
    echo "Perfil no soportado: $PROFILE" >&2
    exit 2
    ;;
esac

rm -rf "$WORKDIR"
mkdir -p "$WORKDIR"
cd "$WORKDIR"

git clone --filter=blob:none https://github.com/termux/termux-packages.git
cd termux-packages
git checkout "$TERMUX_PACKAGES_COMMIT"

python3 - <<'PY'
from pathlib import Path
p = Path("scripts/properties.sh")
s = p.read_text()

replacements = {
    'TERMUX__NAME="Termux"': 'TERMUX__NAME="AngCode"',
    'TERMUX_APP__PACKAGE_NAME="com.termux"': 'TERMUX_APP__PACKAGE_NAME="com.kankwj.angcode"',
}

for old, new in replacements.items():
    if old not in s:
        raise SystemExit(f"Expected property not found: {old}")
    s = s.replace(old, new, 1)

p.write_text(s)
PY

# Validate that the derived paths are actually for AngCode.
bash -lc '. scripts/properties.sh;   test "$TERMUX_APP__PACKAGE_NAME" = "'"$ANGCODE_APP_PACKAGE"'";   test "$TERMUX_APP__DATA_DIR" = "'"$ANGCODE_DATA_DIR"'";   case "$TERMUX_PREFIX" in "'"$ANGCODE_DATA_DIR"'"/*) ;; *) exit 23 ;; esac;   printf "package=%s\ndata=%s\nprefix=%s\n" "$TERMUX_APP__PACKAGE_NAME" "$TERMUX_APP__DATA_DIR" "$TERMUX_PREFIX"'

./scripts/run-docker.sh ./scripts/build-bootstraps.sh   --architectures "$ARCH"   --add "$EXTRA_PACKAGES"

BOOTSTRAP="bootstrap-${ARCH}.zip"
test -f "$BOOTSTRAP"

mkdir -p "$WORKDIR/out"
cp "$BOOTSTRAP" "$WORKDIR/out/angcode-bootstrap-${PROFILE}-${ARCH}.zip"

cd "$WORKDIR/out"
sha256sum "angcode-bootstrap-${PROFILE}-${ARCH}.zip" > "angcode-bootstrap-${PROFILE}-${ARCH}.zip.sha256"

cat > "runtime-manifest-${PROFILE}-${ARCH}.txt" <<EOF
project=AngCode
package=$ANGCODE_APP_PACKAGE
architecture=$ARCH
profile=$PROFILE
termux_packages_commit=$TERMUX_PACKAGES_COMMIT
source=https://github.com/termux/termux-packages
extra_packages=$EXTRA_PACKAGES
EOF

echo "Runtime listo en: $WORKDIR/out"
ls -lh "$WORKDIR/out"
