#!/bin/sh
set -eu

cd "$(dirname "$0")"

cp='bin;lib/sqlite-jdbc-3.53.2.0.jar;lib/slf4j-api-2.0.16.jar;lib/logback-classic-1.5.7.jar;lib/logback-core-1.5.7.jar;lib/gson-2.11.0.jar'
javafxModulePath='lib/javafx-base-25.0.1-win.jar;lib/javafx-graphics-25.0.1-win.jar;lib/javafx-controls-25.0.1-win.jar'
compileCp="$cp;$javafxModulePath"

mkdir -p bin
find src -name '*.java' -print | xargs javac -cp "$compileCp" -d bin
mkdir -p bin/chatmap/infrastructure/persistence/sqlite
cp src/chatmap/infrastructure/persistence/sqlite/schema.sql \
  bin/chatmap/infrastructure/persistence/sqlite/schema.sql

java \
  --module-path "$javafxModulePath" \
  --add-modules javafx.controls \
  --enable-native-access=ALL-UNNAMED,javafx.graphics \
  -cp "$cp" \
  chatmap.presentation.ui.ChatMapLauncher
