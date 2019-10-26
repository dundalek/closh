(ns closh.test-util.spawn-helper
  (:require [clojure.string]
            [closh.zero.parser]
            [closh.zero.compiler]
            [closh.zero.builtin]
            [closh.zero.env]
            #?(:clj [clojure.tools.reader.reader-types :refer [string-push-back-reader]])
            #?(:clj [closh.zero.reader :refer [read-sh]])
            #?(:clj [closh.zero.pipeline :refer [wait-for-pipeline]])
            #?(:clj [closh.zero.platform.eval :as eval])
            #?(:cljs [closh.zero.platform.eval :refer [execute-command-text]])
            #?(:cljs [closh.zero.core])
            [closh.zero.platform.process :as process]
            [closh.zero.macros #?(:cljs :refer-macros :clj :refer) [sh]]))

(defn -main [cmd]
  #?(:cljs (closh.zero.platform.eval/execute-text
             (str (pr-str closh.zero.env/*closh-environment-requires*)))
     :clj (eval/eval-closh-requires))
  (let [result #?(:cljs (execute-command-text cmd)
                  :clj (eval/eval (read-sh (string-push-back-reader cmd))))]
    (cond
      (process/process? result)
      (process/exit (process/exit-code result))

      (and (seq? result)
           (every? #(process/process? %) result))
      (process/exit (process/exit-code (last result)))

      :else
      (print (str result)))))
