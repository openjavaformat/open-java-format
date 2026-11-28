#!/bin/sh
# Usage: open-java-format-native/deb/test-deb.sh <deb> <version>
#
# Runs as root in a fresh Ubuntu or Debian container: installs the package with apt, which brings its
# dependencies, then checks that /usr/bin/open-java-format runs and prints <version>, and formats a file.
set -eu

if [ $# -ne 2 ]; then
  echo "Usage: $0 <deb> <version>" >&2
  exit 2
fi
deb=$1
version=$2

export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
set -x
apt-get install -qq --no-install-recommends "$deb"

test "$(command -v open-java-format)" = /usr/bin/open-java-format
# It prints the version on stderr.
test "$(open-java-format --version 2>&1)" = "open-java-format: Version $version"
cd "$(mktemp -d)"
printf 'class A{int x;}\n' > A.java
open-java-format --replace A.java
printf 'class A {\n    int x;\n}\n' | diff - A.java
