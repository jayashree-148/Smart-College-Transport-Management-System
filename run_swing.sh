#!/bin/sh
# The original Swing desktop app (same planner + MySQL).
cd "$(dirname "$0")" || exit 1
mkdir -p out
javac -cp "lib/*" -d out $(find src -name '*.java') || exit 1
java -cp "out:lib/*" kalvi.transport.TransportApp "$@"
