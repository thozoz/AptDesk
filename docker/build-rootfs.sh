#!/bin/bash
set -e

cd "$(dirname "$0")"

# Fix CRLF in Dockerfile
sed -i 's/\r$//' Dockerfile.ubuntu-xfce 2>/dev/null || true

NO_CACHE=""
if [ "$1" = "--no-cache" ]; then
    NO_CACHE="--no-cache"
fi

echo "[+] Building AptDesk Ubuntu XFCE Rootfs for ARM64..."
docker buildx build $NO_CACHE --platform linux/arm64 \
    -t aptdesk-rootfs:arm64 \
    -f Dockerfile.ubuntu-xfce .

echo "[+] Stripping rootfs bloat (apt cache, docs, man pages, surplus locales)..."
# Run cleanup in a throwaway container against the built image, then commit the
# result back to aptdesk-rootfs:arm64 so the export step below picks up the
# slimmed filesystem. Runs after all package installs from
# Dockerfile.ubuntu-xfce have completed. apt/dpkg binaries and the dpkg status
# DB are deliberately NOT touched -- software management must keep working
# (apt-get update regenerates /var/lib/apt/lists at runtime, same as today).
# Each step is guarded so a missing path never fails the build.
CLEANUP_CONTAINER=$(docker run -d --platform linux/arm64 aptdesk-rootfs:arm64 /bin/bash -c '
apt-get clean || true
rm -rf /var/lib/apt/lists/* || true
rm -rf /usr/share/doc/* || true
rm -rf /usr/share/man/* || true
# Keep C / C.UTF-8 / en* locales (matches ENV LANG=en_US.UTF-8); strip the rest.
if [ -d /usr/share/locale ]; then
    find /usr/share/locale -mindepth 1 -maxdepth 1 \
        ! -name "C" ! -name "C.UTF-8" ! -name "en*" \
        -exec rm -rf {} + 2>/dev/null || true
fi
find /usr -name "*.pyc" -delete 2>/dev/null || true
find / -xdev -type d -name "__pycache__" -exec rm -rf {} + 2>/dev/null || true
rm -rf /tmp/* /var/tmp/* || true
')
docker wait $CLEANUP_CONTAINER >/dev/null || echo "[!] Cleanup step encountered an issue (continuing -- non-fatal)"
docker commit $CLEANUP_CONTAINER aptdesk-rootfs:arm64 >/dev/null
docker rm $CLEANUP_CONTAINER >/dev/null

echo "[+] Exporting filesystem to tarball..."
CONTAINER=$(docker create aptdesk-rootfs:arm64)
docker export $CONTAINER | gzip > aptdesk-rootfs-arm64.tar.gz
docker rm $CONTAINER

echo "[+] Generating SHA256 checksum..."
sha256sum aptdesk-rootfs-arm64.tar.gz > aptdesk-rootfs-arm64.tar.gz.sha256

echo "[+] Done!"
echo "Size: $(du -h aptdesk-rootfs-arm64.tar.gz | cut -f1)"
