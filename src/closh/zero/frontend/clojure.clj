(ns closh.zero.frontend.clojure
  (:require [clojure.main :refer [repl repl-requires skip-whitespace skip-if-eol]]))

(defn repl-read
  "Default :read hook for repl. Reads from *in* which must either be an
  instance of LineNumberingPushbackReader or duplicate its behavior of both
  supporting .unread and collapsing all of CR, LF, and CRLF into a single
  \\newline. repl-read:
    - skips whitespace, then
      - returns request-prompt on start of line, or
      - returns request-exit on end of stream, or
      - reads an object from the input stream, then
        - skips the next input character if it's end of line, then
        - returns the object."
  [request-prompt request-exit]
  (or ({:line-start request-prompt :stream-end request-exit}
       (skip-whitespace *in*))
      (let [input (read {:read-cond :allow} *in*)]
        (skip-if-eol *in*)
        input)))

(defn repl-opt
  "Start a repl with args and inits. Print greeting if no eval options were
  present"
  [[_ & args] inits]
  (println "Clojure REPL" (clojure-version))
  (repl :init (fn []
                (apply require repl-requires))
        :read repl-read)
  (prn)
  (System/exit 0))

(defn -main []
  (repl-opt nil nil))
