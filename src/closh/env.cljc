(ns closh.env)

(def ^:dynamic *closh-aliases* (atom {}))
(def ^:dynamic *closh-abbreviations* (atom {}))
(def ^:dynamic *closh-commands* {})

(def ^:dynamic *closh-environment-init*
  '(do
     (require '[closh.zero.platform.process]
              '[closh.reader]
              '[closh.compiler]
              '[closh.parser]
              '[closh.core :refer [shx expand expand-partial expand-redirect]]
              '[closh.builtin :refer [cd exit quit getenv setenv]]
              '[closh.zero.platform.process]
              '[closh.zero.pipeline :refer [process-output process-value wait-for-pipeline pipe pipe-multi pipe-map pipe-filter pipeline-condition]]
              '[clojure.string :as st]
              '[closh.macros #?(:clj :refer :cljs :refer-macros) [sh sh-str sh-code sh-ok sh-seq sh-lines sh-value defalias defabbr defcmd]]
              #?(:cljs '[lumo.io :refer [slurp spit]])
              #?(:cljs '[closh.util :refer [source-shell]]))

     (defn closh-prompt []
       "$ ")

     (defn closh-title []
       (str "closh " (closh.zero.platform.process/cwd)))

     ;; Return nil otherwise #'cljs.user/closh-prompt got printed every time exception was thrown
     nil))
