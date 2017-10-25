#!/bin/bash

# Play a demo of shell interaction in terminal

# Install dependency with:
# sudo apt install randtype

echo "> closh"
(sleep 2; randtype << END) | node bin/closh.js
date
(def x "Clojure")
(str "Hello " x)
echo (+ 3 4)
pwd
cd src/closh
wc -l *
wc -l * |> (last)
ls | wc -l
ls |> (count)
ls |> (reverse)
ls |> (group-by #(re-find #"[^.]*$" %))
(for [[k v] *1] (str k ": " (count v))) | cat
echo The End
END
