#!/usr/bin/env bash
set -euo pipefail

TERMUX_PACKAGES_COMMIT="${TERMUX_PACKAGES_COMMIT:-2d31765cdab30bbf92f87c495bef6b963df168e5}"
ARCH="${ARCH:-aarch64}"
PROFILE="${PROFILE:-minimal}"
WORKDIR="${WORKDIR:-$PWD/.angcode-runtime-build}"

ANGCODE_APP_PACKAGE="com.kankwj.angcode"
ANGCODE_APP_NAME="AngCode"
ANGCODE_DATA_DIR="/data/data/${ANGCODE_APP_PACKAGE}"
ANGCODE_BUILT_MARKERS="/data/data/.built-packages-angcode"

case "$ARCH" in
  aarch64|arm|i686|x86_64) ;;
  *) echo "Arquitectura no soportada: $ARCH" >&2; exit 2 ;;
esac

case "$PROFILE" in
  minimal)
    EXTRA_PACKAGES="proot,proot-distro,git,libcurl,openssh"
    ;;
  dev)
    EXTRA_PACKAGES="proot,proot-distro,git,libcurl,openssh,python,nodejs-lts,clang,cmake,ninja,make,sqlite,jq,llama-cpp"
    ;;
  full)
    EXTRA_PACKAGES="proot,proot-distro,git,libcurl,openssh,python,nodejs-lts,clang,cmake,ninja,make,sqlite,jq,llama-cpp,ffmpeg,imagemagick,tree-sitter,openjdk-21,gradle,aapt,aapt2,d8,apksigner,android-tools,ecj"
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

# 1) Fork identity / prefix.
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

# 2) Never reuse Docker-image build markers produced for com.termux.
p = Path("scripts/build/termux_step_setup_variables.sh")
s = p.read_text()
old = 'TERMUX_BUILT_PACKAGES_DIRECTORY="/data/data/.built-packages"'
new = 'TERMUX_BUILT_PACKAGES_DIRECTORY="/data/data/.built-packages-angcode"'
if old not in s:
    raise SystemExit("Expected built-packages marker path not found")
s = s.replace(old, new, 1)
p.write_text(s)

# 3) build-bootstraps.sh has its own marker path and an obsolete source
# package name. Modern termux-packages builds bzip2 from packages/libbz2.
p = Path("scripts/build-bootstraps.sh")
s = p.read_text()
old_marker = 'TERMUX_BUILT_PACKAGES_DIRECTORY="/data/data/.built-packages"'
if old_marker not in s:
    raise SystemExit("Bootstrap marker path not found")
s = s.replace(
    old_marker,
    'TERMUX_BUILT_PACKAGES_DIRECTORY="/data/data/.built-packages-angcode"',
    1,
)
old_bzip = 'PACKAGES+=("bzip2")'
if old_bzip not in s:
    raise SystemExit("Expected obsolete bzip2 bootstrap entry not found")
s = s.replace(old_bzip, 'PACKAGES+=("libbz2")', 1)
p.write_text(s)
PY

# Validate fork identity and compatibility patches before starting a long build.
(
  # shellcheck disable=SC1091
  . scripts/properties.sh
  test "$TERMUX_APP__PACKAGE_NAME" = "$ANGCODE_APP_PACKAGE"
  test "$TERMUX_APP__DATA_DIR" = "$ANGCODE_DATA_DIR"
  case "$TERMUX_PREFIX" in
    "$ANGCODE_DATA_DIR"/*) ;;
    *) echo "Prefix inesperado: $TERMUX_PREFIX" >&2; exit 23 ;;
  esac
  printf "package=%s\ndata=%s\nprefix=%s\n" \
    "$TERMUX_APP__PACKAGE_NAME" "$TERMUX_APP__DATA_DIR" "$TERMUX_PREFIX"
)
grep -Fq 'PACKAGES+=("libbz2")' scripts/build-bootstraps.sh
grep -Fq '.built-packages-angcode' scripts/build/termux_step_setup_variables.sh

# Clean only AngCode-specific state inside the builder. Never use bootstrap -f:
# upstream -f can expand an unset arch marker path and become dangerously broad.
./scripts/run-docker.sh bash -lc \
  "rm -rf '$ANGCODE_DATA_DIR' '$ANGCODE_BUILT_MARKERS' && mkdir -p '$ANGCODE_DATA_DIR'"

./scripts/run-docker.sh ./scripts/build-bootstraps.sh \
  --architectures "$ARCH" \
  --add "$EXTRA_PACKAGES"

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
package_alias_curl=libcurl
extra_packages=$EXTRA_PACKAGES
built_markers=$ANGCODE_BUILT_MARKERS
bootstrap_source_patch=bzip2-to-libbz2
EOF

echo "Runtime listo en: $WORKDIR/out"
ls -lh "$WORKDIR/out"
