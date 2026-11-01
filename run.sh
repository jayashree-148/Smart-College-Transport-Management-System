#!/bin/sh
# Starts the Java backend + dashboard and opens it in your browser (loading page -> sign in).
#   ./run.sh              -> MySQL (downloads the driver and builds the database the first time;
#                            falls back to demo files if MySQL cannot be used, and the page says why)
#   ./run.sh --demo       -> demo files only, no MySQL needed
#   ./run.sh --no-browser -> do not open the browser
cd "$(dirname "$0")" || exit 1
if ! command -v javac >/dev/null 2>&1; then echo "Java JDK 17 or newer is required (javac not found). Install it from https://adoptium.net"; exit 1; fi
rm -rf out && mkdir -p out lib
javac -cp "lib/*" -d out $(find src -name '*.java') || { echo "Compile failed - you need JDK 17 or newer:"; java -version; exit 1; }
java -cp "out:lib/*" kalvi.transport.web.WebApp "$@"
