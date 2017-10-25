#!/bin/bash

# Record a demo to gif

# Install dependencies with:
# sudo apt install randtype
# go get github.com/sugyan/ttyrec2gif

ttyrecord=ttyrecord
gifout=doc/img/demo.gif

ttyrec -e "./scripts/demo-play.sh" $ttyrecord
ttyrec2gif -in $ttyrecord -out $gifout
rm $ttyrecord
