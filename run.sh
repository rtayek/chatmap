#!/bin/sh
set -eu

cd "$(dirname "$0")"

cp='bin;lib/sqlite-jdbc-3.53.2.0.jar;lib/slf4j-api-2.0.18.jar;lib/slf4j-nop-2.0.18.jar'
javafxModulePath='lib/javafx-base-25.0.1-win.jar;lib/javafx-graphics-25.0.1-win.jar;lib/javafx-controls-25.0.1-win.jar'

java \
  --module-path "$javafxModulePath" \
  --add-modules javafx.controls \
  --enable-native-access=ALL-UNNAMED \
  -cp "$cp" \
  chatmap.ui.ChatMapLauncher
