#!/bin/sh
set -eu

base=${PWD##*/}
out="$HOME/outgoing/$base.tar"

tar -cf "$out" \
    src \
    tst \
    handoffs \
    build.* \
    *.md

echo "Wrote $out"
