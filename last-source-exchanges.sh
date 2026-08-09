#!/bin/sh
set -eu

cd "$(dirname "$0")"

die() {
  printf '%s\n' "$*" >&2
  exit 1
}

[ -x "./gradlew" ] || die "Gradle wrapper is not executable. Run: chmod +x gradlew"

./gradlew -q classes

case "$(uname -s 2>/dev/null || printf unknown)" in
  MINGW*|MSYS*|CYGWIN*) cpsep=';' ;;
  *) cpsep=':' ;;
esac

cp=".gradle-build/classes/java/main${cpsep}.gradle-build/resources/main${cpsep}lib/gson-2.11.0.jar"

java -cp "$cp" chatmap.cli.LiveSourceExchanges
