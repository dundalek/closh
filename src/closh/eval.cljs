(ns closh.eval
  (:require [lumo.repl]))

;; Make lumo's print a noop since we process resulting value ourselves
(aset js/$$LUMO_GLOBALS "doPrint" identity)

(defn execute-text
  "Evals a string via lumo."
  [source]
  ;; Execute does not return value but binds it to *1
  (lumo.repl/execute-text source {:expression? true})
  *1)

(execute-text
  (pr-str
    '(do
       (require '[lumo.io :refer [slurp spit]]
                '[closh.core :refer [shx expand expand-partial expand-command expand-redirect pipe pipe-multi pipe-map pipe-filter process-output wait-for-process wait-for-pipeline pipeline-condition]]
                '[closh.builtin :refer [cd exit quit]]
                '[clojure.string :as str])
       (require-macros '[closh.core :refer [sh sh-str sh-code sh-ok sh-seq sh-lines]]))))
