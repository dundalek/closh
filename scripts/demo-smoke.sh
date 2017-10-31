#!/bin/bash

# Play commands featured in readme, can be used like a smoke test.

# Install dependency with:
# sudo apt install randtype

echo "> closh"
(sleep 2; while read line; do echo "$line" | randtype; sleep 0.3; done << END) | node bin/closh.js
echo hi
git status
ls -l *.json
(+ 1 2)
echo hi | (clojure.string/upper-case)
ls *.json |> (reverse)
ls |> (group-by first) | (map #(update % 1 count)) | (sort-by second) | (reverse)
END
