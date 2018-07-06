(ns closh.macros
  (:require [closh.parser]
            [closh.compiler]))

(defmacro sh
  "Expands tokens in command mode to executable code."
  [& tokens]
  (closh.compiler/compile-interactive (closh.parser/parse tokens)))

(defmacro sh-value
  "Expands tokens in command mode to executable code."
  [& tokens]
  `(-> ~(closh.compiler/compile-batch (closh.parser/parse tokens))
       (closh.zero.pipeline/process-value)))

(defmacro sh-str
  "Expands command mode returning process output as string."
  [& tokens]
  `(-> ~(closh.compiler/compile-batch (closh.parser/parse tokens))
       (closh.zero.pipeline/process-output)
       (clojure.string/trim)))

(defmacro sh-seq
  "Expands command mode collecting process output returning it as a sequence of strings split by whitespace."
  [& tokens]
  `(-> ~(closh.compiler/compile-batch (closh.parser/parse tokens))
       (closh.zero.pipeline/process-output)
       (clojure.string/trim)
       (clojure.string/split #"\s+")))

(defmacro sh-lines
  "Expands command mode collecting process output returning it as a sequence of lines."
  [& tokens]
  `(-> ~(closh.compiler/compile-batch (closh.parser/parse tokens))
       (closh.zero.pipeline/process-output)
       (clojure.string/trim)
       (clojure.string/split #"\n")))

(defmacro sh-code
  "Expands command mode returning process exit code."
  [& tokens]
  `(-> ~(closh.compiler/compile-interactive (closh.parser/parse tokens))
       (closh.zero.platform.process/wait)
       (closh.zero.platform.process/exit-code)))

(defmacro sh-ok
  "Expands command mode returning true if process completed with non-zero exit code."
  [& tokens]
  `(-> ~(closh.compiler/compile-interactive (closh.parser/parse tokens))
       (closh.zero.platform.process/wait)
       (closh.zero.platform.process/exit-code)
       (zero?)))

(defmacro defalias [name value]
  `(swap! closh.env/*closh-aliases* assoc (str (quote ~name)) ~value))

(defmacro defabbr [name value]
  `(swap! closh.env/*closh-abbreviations* assoc (str (quote ~name)) ~value))

(defmacro defcmd [name & body]
  (if (= 1 (count body))
    `(do (set! closh.env/*closh-commands* (assoc closh.env/*closh-commands* (quote ~name) ~(first body)))
         nil)
    `(do (defn ~name ~@body)
         (defcmd ~name ~name))))
