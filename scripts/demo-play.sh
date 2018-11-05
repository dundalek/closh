#!/bin/bash

# Play a demo of shell interaction in terminal

# Install dependency with:
# sudo apt install randtype expect-dev

# KEY_UP=$'\e'[A
# echo -n $KEY_UP

# Alternatives:
# (for [[k v] *1] (str k ": " (count v))) | cat
# (reduce (fn [m [k v]] (assoc m k (count v))) {} *1)
# (map (fn [[k v]] [k (count v)]) *1) | (into {})
# (for [[k v] *1] [k (count v)]) | (into {})

echo "> closh"
(sleep 6; while read line; do echo "$line" | randtype -t 8,40000; sleep 0.5; done << END) | unbuffer -p clojure -m closh.zero.frontend.rebel
date
(def x "Clojure")
(str "Hello " x)
echo (+ 3 4)
pwd
cd src/closh/zero/platform
wc -l *
wc -l * |> (last)
ls | wc -l
ls |> (count)
ls |> (reverse)
(defn extension [s] (re-find #"[^.]*$" s))
ls |> (group-by extension)
(for [[k v] *1] (str k ": " (count v))) | cat
echo The End
END
