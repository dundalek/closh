(ns closh.zero.frontend.main  
  (:require [clojure.main :refer [repl repl-requires]]
            [closh.zero.reader :refer [read-sh]]
            [closh.zero.platform.process :refer [process?]]
            [closh.zero.env :refer [*closh-environment-init*]]))

(defn repl-read
  [request-prompt request-exit]
  (let [input (read-sh {:read-cond :allow} *in*)]
    (list 'closh.zero.pipeline/wait-for-pipeline input)))

(defn repl-print
  [& args]
  (when-not (or (nil? (first args))
                (process? (first args)))
    (apply prn args)))

(defn repl-opt
  [[_ & args] inits]
  (repl :init (fn []
                (apply require repl-requires)
                (eval *closh-environment-init*))
        :read repl-read
        :print repl-print)
  (prn)
  (System/exit 0))

(defn -main []
  (repl-opt nil nil))
