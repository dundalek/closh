(ns closh.core
  (:require [clojure.string]
            [closh.parser]))

; (macroexpand '(sh ls -l))
; (macroexpand '(sh git commit -a |> #(cat %) | head -n 10))

(defmacro sh [& tokens]
  (closh.parser/parse tokens))

(defmacro sh-str [& tokens]
  `(-> ~(closh.parser/parse tokens)
       process-output
       clojure.string/trim))
