#!/bin/bash

./closh-zero-sci -e "(+ 1 2)"
./closh-zero-sci -e "date"
./closh-zero-sci -e 'echo "hi" | (clojure.string/upper-case)'
./closh-zero-sci fixtures/script-mode-tests/cond.cljc
./closh-zero-sci fixtures/script-mode-tests/bar.cljc
echo "(+ 1 2)" | ./closh-zero-sci -
echo date | ./closh-zero-sci -
