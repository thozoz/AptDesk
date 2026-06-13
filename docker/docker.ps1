param([switch]$NoCache)

$script = @'
set -e
cd /mnt/c/Users/efe05/OneDrive/Documentos/GitHub/AptDesk/docker
sed -i 's/\r$//' Dockerfile.ubuntu-xfce build-rootfs.sh 2>/dev/null
docker run --rm --privileged multiarch/qemu-user-static --reset -p yes > /dev/null 2>&1 || true
'@

if ($NoCache) {
    $script += "`n./build-rootfs.sh --no-cache"
} else {
    $script += "`n./build-rootfs.sh"
}

wsl -e bash -c ($script.Replace("`r`n", "`n"))
