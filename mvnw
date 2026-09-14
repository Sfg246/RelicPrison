#!/bin/sh
set -eu

if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi

MAVEN_VERSION="3.9.16"
MAVEN_HOME_DIR="${MAVEN_USER_HOME:-$HOME/.m2}/wrapper/dists/apache-maven-$MAVEN_VERSION"
MAVEN_BIN="$MAVEN_HOME_DIR/apache-maven-$MAVEN_VERSION/bin/mvn"
DIST_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.zip"

if [ ! -x "$MAVEN_BIN" ]; then
  mkdir -p "$MAVEN_HOME_DIR"
  ARCHIVE="$MAVEN_HOME_DIR/maven.zip"
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location "$DIST_URL" --output "$ARCHIVE"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ARCHIVE" "$DIST_URL"
  else
    echo "Maven is not installed and neither curl nor wget is available." >&2
    exit 1
  fi
  if ! command -v unzip >/dev/null 2>&1; then
    echo "unzip is required for the Maven wrapper fallback." >&2
    exit 1
  fi
  unzip -q -o "$ARCHIVE" -d "$MAVEN_HOME_DIR"
  rm -f "$ARCHIVE"
fi

exec "$MAVEN_BIN" "$@"
