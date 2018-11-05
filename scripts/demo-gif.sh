#!/bin/bash

# Record a demo to gif

# Install dependencies with:
# sudo apt install randtype
# go get github.com/sugyan/ttyrec2gif

# Following prompt is used for recording:
#
# (defn closh-prompt []
#   (str "\u001b[01;32m" (getenv "USER") "\u001b[0m:\u001b[01;34m\u2026/" (last (clojure.string/split (getenv "PWD") #"/")) "\u001b[0m$ "))

# Before recording resize terminal to 80x24 to avoid any visual glitches

ttyrecord=/tmp/ttyrecord-$$
gifout=doc/img/demo.gif

env SHELL=/bin/sh ttyrec -e "./scripts/demo-play.sh" $ttyrecord
ttyrec2gif -in $ttyrecord -out $gifout
rm $ttyrecord

# Make the initial frame display faster
convert $gifout \( -clone 0 -set delay 50 \) -swap 0 +delete $gifout
