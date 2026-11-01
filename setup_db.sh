#!/bin/sh
# Creates college_transport_db and loads sql/01..05 using JDBC (no mysql command line needed).
#   ./setup_db.sh           first time
#   ./setup_db.sh --reset   drop the database and rebuild it from scratch
cd "$(dirname "$0")" || exit 1
mkdir -p out
javac -cp "lib/*" -d out $(find src -name '*.java') || exit 1
java -cp "out:lib/*" kalvi.transport.dao.DbSetup db.properties "$@"
