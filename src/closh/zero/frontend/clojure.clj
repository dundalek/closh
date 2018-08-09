(ns closh.zero.frontend.clojure
  (:require [clojure.main :refer [repl repl-requires]]
            [closh.reader :refer [read-sh]]
            [closh.env :refer [*closh-environment-init*]]))

(defn repl-read
  [request-prompt request-exit]
  (let [input (read-sh {:read-cond :allow} *in*)]
    (list 'closh.zero.pipeline/wait-for-pipeline input)))

(defn repl-opt
  [[_ & args] inits]
  (repl :init (fn []
                (apply require repl-requires)
                (eval *closh-environment-init*))
        :read repl-read)
  (prn)
  (System/exit 0))

(defn -main []
  (repl-opt nil nil))
