#!/bin/bash
set -e

echo "[+] Building AptDesk Ubuntu XFCE Rootfs for ARM64..."

# Build the docker image for ARM64
docker buildx build --platform linux/arm64 \
    -t aptdesk-rootfs:arm64 \
    -f Dockerfile.ubuntu-xfce .

echo "[+] Exporting filesystem to tarball..."
# Create a temporary container
CONTAINER=$(docker create aptdesk-rootfs:arm64)

# Export and compress the filesystem
docker export $CONTAINER | gzip > aptdesk-rootfs-arm64.tar.gz

# Remove the temporary container
docker rm $CONTAINER

echo "[+] Generating SHA256 checksum..."
sha256sum aptdesk-rootfs-arm64.tar.gz > aptdesk-rootfs-arm64.tar.gz.sha256

echo "[+] Done!"
echo "Size: $(du -h aptdesk-rootfs-arm64.tar.gz | cut -f1)"
