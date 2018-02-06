(ns closh.core
  (:require [clojure.string]
            [closh.parser]))

(defmacro sh
  "Expands tokens in command mode to executable code."
  [& tokens]
  (closh.parser/parse-interactive tokens))

(defmacro sh-value
  "Expands tokens in command mode to executable code."
  [& tokens]
  `(-> ~(closh.parser/parse-batch tokens)
       (process-value)))

(defmacro sh-str
  "Expands command mode returning process output as string."
  [& tokens]
  `(-> ~(closh.parser/parse-batch tokens)
       (process-output)
       (clojure.string/trim)))

(defmacro sh-seq
  "Expands command mode collecting process output returning it as a sequence of strings split by whitespace."
  [& tokens]
  `(-> ~(closh.parser/parse-batch tokens)
       (process-output)
       (clojure.string/trim)
       (clojure.string/split  #"\s+")))

(defmacro sh-lines
  "Expands command mode collecting process output returning it as a sequence of lines."
  [& tokens]
  `(-> ~(closh.parser/parse-batch tokens)
       (process-output)
       (clojure.string/trim)
       (clojure.string/split  #"\n")))

(defmacro sh-code
  "Expands command mode returning process exit code."
  [& tokens]
  `(-> ~(closh.parser/parse-interactive tokens)
       (wait-for-process)
       (.-exitCode)))

(defmacro sh-ok
  "Expands command mode returning true if process completed with non-zero exit code."
  [& tokens]
  `(-> ~(closh.parser/parse-interactive tokens)
       (wait-for-process)
       (.-exitCode)
       (zero?)))

(defmacro defalias [name value]
  `(set! closh.core/*closh-aliases* (assoc closh.core/*closh-aliases* ~name ~value)))

(defmacro defabbr [name value]
  `(set! closh.core/*closh-abbreviations* (assoc closh.core/*closh-abbreviations* ~name ~value)))

(defmacro defcmd
  ([name fn]
   `(set! closh.core/*closh-commands* (assoc closh.core/*closh-commands* ~name ~fn))))
  ; ([name &body]
  ;  `(defn ~name ~@body)))
