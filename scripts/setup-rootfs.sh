#!/bin/bash
# scripts/setup-rootfs.sh
# This script is meant to be run once when the proot environment boots up for the first time,
# or to ensure configurations are correct on every boot.

echo "[AptDesk] Setting up rootfs environment..."

# Ensure necessary directories exist
mkdir -p /opt/aptdesk/www
mkdir -p /opt/aptdesk/install-scripts

# Set correct permissions
chown -R user:user /home/user

echo "[AptDesk] Rootfs setup complete."
