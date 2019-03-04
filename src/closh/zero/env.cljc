(ns closh.zero.env)

(def ^:dynamic *closh-aliases* (atom {}))
(def ^:dynamic *closh-abbreviations* (atom {}))
(def ^:dynamic *closh-commands* (atom {}))

(def ^:dynamic *closh-environment-requires*
  '(require '[closh.zero.platform.process]
            '[closh.zero.reader]
            '[closh.zero.compiler]
            '[closh.zero.parser]
            '[closh.zero.core :refer [shx expand]]
            '[closh.zero.builtin :refer [cd exit quit getenv setenv]]
            '[closh.zero.platform.process]
            '[closh.zero.pipeline]
            '[clojure.string :as str]
            '[closh.zero.macros #?(:clj :refer :cljs :refer-macros) [sh sh-str sh-code sh-ok sh-seq sh-lines sh-value defalias defabbr defcmd]]
            '[closh.zero.util :refer [source-shell]]
            #?(:cljs '[lumo.io :refer [slurp spit]])))

(def ^:dynamic *closh-environment-init*
  '(do
     (def ^:dynamic closh-prompt (fn [] "$ "))

     (def ^:dynamic closh-title (fn [] (str "closh " (closh.zero.platform.process/cwd))))

     ;; Return nil otherwise #'cljs.user/closh-prompt got printed every time exception was thrown
     nil))
