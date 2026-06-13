#!/bin/bash
set -e

cd "$(dirname "$0")"

# Fix CRLF
sed -i 's/\r$//' Dockerfile.ubuntu-xfce
sed -i 's/\r$//' build-rootfs.sh

NO_CACHE=""
if [ "$1" = "--no-cache" ]; then
    NO_CACHE="--no-cache"
fi

echo "[+] Building AptDesk Ubuntu XFCE Rootfs for ARM64..."
docker buildx build $NO_CACHE --platform linux/arm64 \
    -t aptdesk-rootfs:arm64 \
    -f Dockerfile.ubuntu-xfce .

echo "[+] Exporting filesystem to tarball..."
CONTAINER=$(docker create aptdesk-rootfs:arm64)
docker export $CONTAINER | gzip > aptdesk-rootfs-arm64.tar.gz
docker rm $CONTAINER

echo "[+] Generating SHA256 checksum..."
sha256sum aptdesk-rootfs-arm64.tar.gz > aptdesk-rootfs-arm64.tar.gz.sha256

echo "[+] Done!"
echo "Size: $(du -h aptdesk-rootfs-arm64.tar.gz | cut -f1)"
