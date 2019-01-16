(ns closh.zero.macros
  (:require [closh.zero.parser]
            [closh.zero.compiler]
            [closh.zero.pipeline]))

(defmacro sh
  "Expands tokens in command mode to executable code."
  [& tokens]
  `(-> ~(closh.zero.compiler/compile-interactive (closh.zero.parser/parse tokens))
       (closh.zero.pipeline/wait-for-pipeline)))

(defmacro sh-value
  "Expands tokens in command mode to executable code."
  [& tokens]
  `(-> ~(closh.zero.compiler/compile-batch (closh.zero.parser/parse tokens))
       (closh.zero.pipeline/process-value)))

(defmacro sh-val
  "Expands command mode returning process output as output value."
  [& tokens]
  `(-> ~(closh.zero.compiler/compile-batch (closh.zero.parser/parse tokens))
       (closh.zero.pipeline/process-output)))

(defmacro sh-str
  "Expands command mode returning process output as string."
  [& tokens]
  `(-> ~(closh.zero.compiler/compile-batch (closh.zero.parser/parse tokens))
       (closh.zero.pipeline/process-output)
       (str)
       (clojure.string/trim)))

(defmacro sh-seq
  "Expands command mode collecting process output returning it as a sequence of strings split by whitespace."
  [& tokens]
  `(-> ~(closh.zero.compiler/compile-batch (closh.zero.parser/parse tokens))
       (closh.zero.pipeline/process-output)
       (clojure.string/trim)
       (clojure.string/split #"\s+")))

(defmacro sh-lines
  "Expands command mode collecting process output returning it as a sequence of lines."
  [& tokens]
  `(-> ~(closh.zero.compiler/compile-batch (closh.zero.parser/parse tokens))
       (closh.zero.pipeline/process-output)
       (clojure.string/trim)
       (clojure.string/split #"\n")))

(defmacro sh-code
  "Expands command mode returning process exit code."
  [& tokens]
  `(-> ~(closh.zero.compiler/compile-interactive (closh.zero.parser/parse tokens))
       (closh.zero.platform.process/wait)
       (closh.zero.platform.process/exit-code)))

(defmacro sh-ok
  "Expands command mode returning true if process completed with non-zero exit code."
  [& tokens]
  `(-> ~(closh.zero.compiler/compile-interactive (closh.zero.parser/parse tokens))
       (closh.zero.platform.process/wait)
       (closh.zero.platform.process/exit-code)
       (zero?)))

(defmacro sh-wrapper [& tokens]
  "Like sh macro but if the result is a process then returns nil. This is useful for eval mode so that process objects are not printed out."
  `(let [result# (sh ~@tokens)]
     (when-not (closh.zero.platform.process/process? result#) result#)))

(defmacro defalias [name value]
  `(swap! closh.zero.env/*closh-aliases* assoc (str (quote ~name)) ~value))

(defmacro defabbr [name value]
  `(swap! closh.zero.env/*closh-abbreviations* assoc (str (quote ~name)) ~value))

(defmacro defcmd [name & body]
  (if (= 1 (count body))
    `(do (swap! closh.zero.env/*closh-commands* assoc (quote ~name) ~(first body))
         nil)
    `(do (defn ~name ~@body)
         (defcmd ~name ~name))))

(defmacro chain-> [x & forms]
  `(-> ~x ~@(for [form forms]
              #?(:clj (list form)
                 :cljs (list '.then form)))))

(comment
  (macroexpand-1 '(chain-> x (first) (second))))
