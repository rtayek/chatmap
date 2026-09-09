#!/bin/sh
set -eu

base=${PWD##*/}
out="$HOME/outgoing/$base.tar"

tar -cf "$out" \
    src \
    tst \
    .llm \
    *.sh  \
    *.md

echo "Wrote $out"
