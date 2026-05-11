#!/usr/bin/env bash
# NeXiS Worker — Installer
# Sets up the desktop app and all required system dependencies.
set -euo pipefail

REPO="santiagotoro2023/nexis-worker"
API="https://api.github.com/repos/${REPO}/releases/latest"

log() { printf '\033[0;36m==> %s\033[0m\n' "$*"; }
die() { printf '\033[0;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || die "Run with sudo: curl -sSL ... | sudo bash"

log "Detecting platform..."
OS="$(uname -s)"
ARCH="$(uname -m)"

# ── System dependencies ──────────────────────────────────────────────────────
log "Installing system dependencies..."
if command -v apt-get &>/dev/null; then
    apt-get update -qq
    # x11vnc: VNC server for screen sharing
    # python3-pip / websockify: WebSocket proxy used by noVNC remote screen
    DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
        x11vnc \
        python3-pip \
        python3-websockify \
        xdotool \
        xclip \
        libnotify-bin \
        2>/dev/null || true
    # Ensure websockify is also available as standalone command
    pip3 install --quiet --break-system-packages websockify 2>/dev/null || \
    pip3 install --quiet websockify 2>/dev/null || true
elif command -v brew &>/dev/null; then
    brew install --quiet websockify x11vnc 2>/dev/null || true
    pip3 install --quiet websockify 2>/dev/null || true
elif command -v pacman &>/dev/null; then
    pacman -Sy --noconfirm x11vnc python-websockify 2>/dev/null || true
fi

# ── Download latest release ──────────────────────────────────────────────────
log "Fetching latest release..."
RELEASE=$(curl -fsSL "${API}")
VERSION=$(echo "$RELEASE" | grep '"tag_name"' | head -1 | sed 's/.*"v\?\([^"]*\)".*/\1/')
log "Version: ${VERSION}"

case "${OS}-${ARCH}" in
    Linux-x86_64)   ASSET_PAT="nexis-worker_${VERSION}_amd64.deb" ;;
    Linux-aarch64)  ASSET_PAT="nexis-worker_${VERSION}_arm64.deb" ;;
    Darwin-*)        ASSET_PAT="nexis-worker_${VERSION}.dmg"       ;;
    *)               die "Unsupported platform: ${OS}-${ARCH}" ;;
esac

DL_URL=$(echo "$RELEASE" | grep '"browser_download_url"' | grep "${ASSET_PAT}" | sed 's/.*"\(https[^"]*\)".*/\1/')
[[ -n "${DL_URL}" ]] || die "No release asset found matching: ${ASSET_PAT}"

TMPFILE=$(mktemp /tmp/nexis-worker-XXXX)
trap "rm -f ${TMPFILE}" EXIT

log "Downloading ${ASSET_PAT}..."
curl -fsSL --progress-bar "${DL_URL}" -o "${TMPFILE}"

# ── Install ──────────────────────────────────────────────────────────────────
log "Installing..."
case "${ASSET_PAT}" in
    *.deb) dpkg -i "${TMPFILE}" && apt-get install -f -y -qq 2>/dev/null || true ;;
    *.dmg)
        VOLUME=$(hdiutil attach "${TMPFILE}" -nobrowse | grep Volumes | awk '{print $NF}')
        cp -R "${VOLUME}"/*.app /Applications/
        hdiutil detach "${VOLUME}" -quiet
        ;;
esac

# ── Summary ──────────────────────────────────────────────────────────────────
log "Done."
echo ""
echo "  NeXiS Worker v${VERSION} installed."
echo ""
echo "  Launch: search for 'NeXiS Worker' in your app launcher"
echo "  On first run, enter your NeXiS Controller URL and credentials."
echo ""
echo "  Screen sharing (VNC) requires x11vnc — installed automatically."
echo "  websockify required for noVNC remote screen — installed automatically."
echo ""
