(ns closh.core
  (:require [clojure.string]
            [closh.parser]))

(defmacro sh [& tokens]
  (closh.parser/parse tokens))

(defmacro sh-str [& tokens]
  `(-> ~(closh.parser/parse tokens)
       (process-output)
       (clojure.string/trim)))

(defmacro sh-code [& tokens]
  `(-> ~(closh.parser/parse tokens)
       (wait-for-process)
       (.-exitCode)))

(defmacro sh-ok [& tokens]
  `(-> ~(closh.parser/parse tokens)
       (wait-for-process)
       (.-exitCode)
       (zero?)))
