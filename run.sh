#!/bin/sh
set -eu

cd "$(dirname "$0")"

cp='bin;lib/sqlite-jdbc-3.53.2.0.jar;lib/slf4j-api-2.0.18.jar;lib/slf4j-nop-2.0.18.jar'
javafxModulePath='lib/javafx-base-25.0.1-win.jar;lib/javafx-graphics-25.0.1-win.jar;lib/javafx-controls-25.0.1-win.jar'
compileCp="$cp;$javafxModulePath"

mkdir -p bin
find src -name '*.java' -print | xargs javac -cp "$compileCp" -d bin
mkdir -p bin/chatmap/storage
cp src/chatmap/storage/schema.sql bin/chatmap/storage/schema.sql

java \
  --module-path "$javafxModulePath" \
  --add-modules javafx.controls \
  --enable-native-access=ALL-UNNAMED,javafx.graphics \
  -cp "$cp" \
  chatmap.ui.ChatMapLauncher
