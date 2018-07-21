(ns closh.zero.frontend.clojure
  (:require [clojure.main :refer [repl repl-requires]]))

(defn repl-opt
  "Start a repl with args and inits. Print greeting if no eval options were
  present"
  [[_ & args] inits]
  (println "Clojure REPL" (clojure-version))
  (repl :init (fn []
                (apply require repl-requires)))
  (prn)
  (System/exit 0))

(defn -main []
  (repl-opt nil nil))
