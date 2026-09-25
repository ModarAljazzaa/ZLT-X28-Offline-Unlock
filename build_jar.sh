#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CLASSES="$SCRIPT_DIR/build-java/classes"
OUTPUT="$SCRIPT_DIR/dist/ZLT-X28-Unlock.jar"

rm -rf -- "$CLASSES"
mkdir -p "$CLASSES" "$SCRIPT_DIR/dist"

javac --release 8 -encoding UTF-8 -d "$CLASSES" "$SCRIPT_DIR/src/ZltX28Unlock.java"
cp "$SCRIPT_DIR/x28.tgz" "$CLASSES/x28.tgz"
jar cfe "$OUTPUT" ZltX28Unlock -C "$CLASSES" .

printf 'Executable JAR created at: %s\n' "$OUTPUT"
