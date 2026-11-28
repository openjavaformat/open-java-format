#!/usr/bin/env bash
# Usage: open-java-format-native/deb/build-deb.sh <binary> <version> <out-dir>
#
# Packages a Linux native binary as open-java-format_<version>_<arch>.deb in <out-dir>, for the GitHub release
# and the apt repository that serves it, openjavaformat/apt. The package installs /usr/bin/open-java-format.
#
# The architecture, amd64 or arm64, is read off the binary's ELF header. So is the libc6 dependency: the newest
# GLIBC_ symbol version that the binary asks for, so that a build on a newer image cannot slip past older
# systems. apt refuses the package there instead of installing a binary that does not start.
#
# SOURCE_DATE_EPOCH, when set, is the modification time of every file in the package, which makes it the same
# bytes on every run.
#
# Needs dpkg-deb, readelf and du, which GitHub's Ubuntu runners have.
set -euo pipefail

if [ $# -ne 3 ]; then
  echo "Usage: $0 <binary> <version> <out-dir>" >&2
  exit 2
fi
binary=$1
version=$2
out=$3
here=$(cd "$(dirname "$0")" && pwd)

# Debian's rule for an upstream version, without an epoch: a digit first, then these characters.
if ! [[ $version =~ ^[0-9][A-Za-z0-9.+~-]*$ ]]; then
  echo "Not a Debian package version: $version" >&2
  exit 1
fi

machine=$(readelf --file-header "$binary" | sed -n 's/^ *Machine: *//p')
case $machine in
  'Advanced Micro Devices X86-64') arch=amd64 ;;
  AArch64) arch=arm64 ;;
  *)
    echo "$binary is not a Linux binary for x86-64 or AArch64: $machine" >&2
    exit 1
    ;;
esac

glibc=$(readelf --version-info --wide "$binary" | grep -o 'GLIBC_[0-9][0-9.]*' | sed 's/^GLIBC_//' | sort -uV \
  | tail -n 1)
if [ -z "$glibc" ]; then
  echo "$binary asks for no GLIBC_ symbol version: not a glibc binary?" >&2
  exit 1
fi

# The modes of the package's directories, mktemp's 0700 included, are those of what it installs.
umask 022
root=$(mktemp -d)
trap 'rm -rf "$root"' EXIT
chmod 0755 "$root"

install -D --mode 0755 "$binary" "$root/usr/bin/open-java-format"
install -D --mode 0644 "$here/copyright" "$root/usr/share/doc/open-java-format/copyright"
mkdir -p "$root/DEBIAN"
cat > "$root/DEBIAN/control" <<CONTROL
Package: open-java-format
Version: $version
Architecture: $arch
Maintainer: Alexey Abashev <alexey@openjavaformat.dev>
Installed-Size: $(du -sk --apparent-size "$root/usr" | cut -f 1)
Depends: libc6 (>= $glibc), zlib1g
Section: devel
Priority: optional
Homepage: https://openjavaformat.dev
Description: modern, lambda-friendly, 120-character Java formatter
 open-java-format reformats Java source files to one consistent style. This
 package is its native command-line binary, which needs no JDK.
CONTROL

epoch=${SOURCE_DATE_EPOCH:-$(date +%s)}
find "$root" -exec touch --no-dereference --date "@$epoch" {} +

mkdir -p "$out"
deb=$out/open-java-format_${version}_$arch.deb
# xz, which every dpkg reads: dpkg-deb's default, zstd, needs dpkg 1.21.18 or later.
SOURCE_DATE_EPOCH=$epoch dpkg-deb --root-owner-group -Zxz --build "$root" "$deb"
dpkg-deb --info "$deb" control
