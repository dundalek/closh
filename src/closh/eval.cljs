(ns closh.eval
  (:require [lumo.repl]
            [cljs.env :as env]
            [cljs.js :as cljs]))

(def ns *ns*)
(def compiler env/*compiler*)
(def eval-fn cljs/*eval-fn*)
(def load-fn cljs/*load-fn*)

(defn eval-cljs [form]
  (binding [cljs/*eval-fn* eval-fn
            cljs/*load-fn* load-fn
            env/*compiler* compiler]
    (lumo.repl/eval form ns)))

(eval-cljs '(require '[closh.core :refer [shx expand expand-partial expand-command expand-redirect pipe pipe-multi pipe-map pipe-filter process-output wait-for-process]]
                     '[clojure.string :as str]))
