#!/bin/bash
# SessionStart hook: make Claude Code on the web sessions able to build this
# Paper 26.x plugin, which needs JDK 25 (not preinstalled in the web image).
#
# Strategy: install Temurin JDK 25 to a cached location and register it as a
# Gradle toolchain, WITHOUT forcing Gradle itself to run on 25 (Gradle 8.14
# runs fine on the image's JDK 21 and uses 25 only to compile/test).
set -euo pipefail

# Only relevant in remote (web) sessions; local machines have their own JDKs.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

JDK_DIR="$HOME/.local/jdk25"

install_jdk25() {
  if [ -x "$JDK_DIR/bin/java" ] && "$JDK_DIR/bin/java" -version 2>&1 | grep -q 'version "25'; then
    echo "JDK 25 already present at $JDK_DIR"
    return 0
  fi
  echo "Installing Temurin JDK 25..."
  local tag ver file_ver asset url tmp
  tag=$(curl -fsSL -o /dev/null -w '%{url_effective}' \
        "https://github.com/adoptium/temurin25-binaries/releases/latest" | sed 's#.*/tag/##')
  ver=${tag#jdk-}
  file_ver=${ver//+/_}
  asset="OpenJDK25U-jdk_x64_linux_hotspot_${file_ver}.tar.gz"
  url="https://github.com/adoptium/temurin25-binaries/releases/download/${tag}/${asset}"
  tmp=$(mktemp -d)
  curl -fsSL -o "$tmp/jdk.tar.gz" "$url"
  rm -rf "$JDK_DIR"
  mkdir -p "$JDK_DIR"
  tar -xzf "$tmp/jdk.tar.gz" -C "$JDK_DIR" --strip-components=1
  rm -rf "$tmp"
  echo "Installed $("$JDK_DIR/bin/java" -version 2>&1 | head -1)"
}

install_jdk25

# Register the toolchain for Gradle (user-level, idempotent).
mkdir -p "$HOME/.gradle"
PROP_FILE="$HOME/.gradle/gradle.properties"
touch "$PROP_FILE"
sed -i '/^org\.gradle\.java\.installations\.paths=/d' "$PROP_FILE"
echo "org.gradle.java.installations.paths=$JDK_DIR" >> "$PROP_FILE"

# Warm the Gradle distribution + dependency cache so the first build is fast.
# Best-effort: never block session startup on a warm failure.
cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}"
./gradlew --no-daemon testClasses >/dev/null 2>&1 || \
  echo "warm build skipped (will resolve on first ./gradlew build)"

echo "Session ready: JDK 25 toolchain registered."
