#!/bin/bash

set -x

CLOSH_CMD=./closh-zero-sci
#CLOSH_CMD="java -jar target/closh-zero-sci.jar"

$CLOSH_CMD -e "(+ 1 2)"
$CLOSH_CMD -e "date"
$CLOSH_CMD -e 'echo "hi" | (clojure.string/upper-case)'
$CLOSH_CMD fixtures/script-mode-tests/cond.cljc
$CLOSH_CMD fixtures/script-mode-tests/bar.cljc
echo "(+ 1 2)" | $CLOSH_CMD -
echo date | $CLOSH_CMD -
$CLOSH_CMD -h

expect -c "
spawn $CLOSH_CMD;
send \"(println :hello-word)\r\";
send \"(exit)\r\";
expect eof;
"
