#!/bin/bash

# Record a demo to gif

# Install dependencies with:
# sudo apt install randtype
# go get github.com/sugyan/ttyrec2gif

# Following prompt is used for recording:
#
# (def decode-prompt (js/require "decode-prompt"))
# (def PS1 "\\[\\033[01;32m\\]\\u\\[\\033[00m\\]:\\[\\033[01;34m\\]\\w\\[\\033[00m\\]\\$ ")
# (defn closh-prompt []
#   (decode-prompt PS1 #js{:env js/process.env
#                          :cwd (path.basename (js/process.cwd))}))

ttyrecord=/tmp/ttyrecord-$$
gifout=doc/img/demo.gif

env SHELL=/bin/sh ttyrec -e "./scripts/demo-play.sh" $ttyrecord
ttyrec2gif -in $ttyrecord -out $gifout
rm $ttyrecord
