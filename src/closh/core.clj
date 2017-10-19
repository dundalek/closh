(ns closh.core
  (:require [clojure.string]
            [closh.parser]))

(defmacro sh [& tokens]
  (closh.parser/parse tokens))

(defmacro sh-str [& tokens]
  `(-> ~(closh.parser/parse tokens)
       process-output
       clojure.string/trim))
