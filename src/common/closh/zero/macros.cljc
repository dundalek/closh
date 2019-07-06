(ns closh.zero.macros
  (:require [clojure.string :as trim]
            [closh.zero.parser :as parser]
            [closh.zero.compiler :as compiler]
            [closh.zero.pipeline :as pipeline]
            [closh.zero.env :as env]
            [closh.zero.platform.process :as process]))

(defmacro sh
  "Expands tokens in command mode to executable code."
  [& tokens]
  `(-> ~(compiler/compile-interactive (parser/parse tokens))
       (pipeline/wait-for-pipeline)))

(defmacro sh-value
  "Expands tokens in command mode to executable code."
  [& tokens]
  `(-> ~(compiler/compile-batch (parser/parse tokens))
       (pipeline/process-value)))

(defmacro sh-val
  "Expands command mode returning process output as output value."
  [& tokens]
  `(-> ~(compiler/compile-batch (parser/parse tokens))
       (pipeline/process-output)))

(defmacro sh-str
  "Expands command mode returning process output as string."
  [& tokens]
  `(-> ~(compiler/compile-batch (parser/parse tokens))
       (pipeline/process-output)
       (str)
       (str/trim)))

(defmacro sh-seq
  "Expands command mode collecting process output returning it as a sequence of strings split by whitespace."
  [& tokens]
  `(-> ~(compiler/compile-batch (parser/parse tokens))
       (pipeline/process-output)
       (str/trim)
       (str/split #"\s+")))

(defmacro sh-lines
  "Expands command mode collecting process output returning it as a sequence of lines."
  [& tokens]
  `(-> ~(compiler/compile-batch (parser/parse tokens))
       (pipeline/process-output)
       (str/trim)
       (str/split #"\n")))

(defmacro sh-code
  "Expands command mode returning process exit code."
  [& tokens]
  `(-> ~(compiler/compile-interactive (parser/parse tokens))
       (process/wait)
       (process/exit-code)))

(defmacro sh-ok
  "Expands command mode returning true if process completed with non-zero exit code."
  [& tokens]
  `(-> ~(compiler/compile-interactive (parser/parse tokens))
       (process/wait)
       (process/exit-code)
       (zero?)))

(defmacro sh-wrapper [& tokens]
  "Like sh macro but if the result is a process then returns nil. This is useful for eval mode so that process objects are not printed out."
  `(let [result# (sh ~@tokens)]
     (when-not (process/process? result#) result#)))

(defmacro defalias [name value]
  `(swap! env/*closh-aliases* assoc (str (quote ~name)) ~value))

(defmacro defabbr [name value]
  `(swap! env/*closh-abbreviations* assoc (str (quote ~name)) ~value))

(defmacro defcmd [name & body]
  (if (= 1 (count body))
    `(do (swap! env/*closh-commands* assoc (quote ~name) ~(first body))
         nil)
    `(do (defn ~name ~@body)
         (defcmd ~name ~name))))

(defmacro chain-> [x & forms]
  `(-> ~x ~@(for [form forms]
              #?(:clj (list form)
                 :cljs (list '.then form)))))

(comment
  (macroexpand-1 '(chain-> x (first) (second))))
