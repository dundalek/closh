(ns closh.eval
  (:require [lumo.repl]))

(defn execute-text [source]
  (lumo.repl/execute-text source {:expression? true}))

(execute-text
  (pr-str
    '(do
       (require '[closh.core :refer [shx expand expand-partial expand-command expand-redirect pipe pipe-multi pipe-map pipe-filter process-output wait-for-process]]
                '[closh.builtin :refer [cd exit quit]]
                '[clojure.string :as str])
       (require-macros '[closh.core :refer [sh]]))))
