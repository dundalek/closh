(ns closh.test-util.spawn-helper
  (:require [clojure.string]
            [closh.parser]
            [closh.compiler]
            [closh.builtin]
            [closh.env]
            #?(:clj [clojure.tools.reader.reader-types :refer [string-push-back-reader]])
            #?(:clj [closh.reader :refer [read-sh]])
            #?(:clj [closh.zero.pipeline :refer [wait-for-pipeline]])
            #?(:cljs [closh.zero.platform.eval :refer [execute-command-text]])
            #?(:cljs [closh.core :refer [handle-line]])
            [closh.zero.platform.process :as process]
            [closh.macros #?(:cljs :refer-macros :clj :refer) [sh]]))

(defn -main [cmd]
  #?(:cljs (closh.zero.platform.eval/execute-text
             (str (pr-str closh.env/*closh-environment-init*)))
     :clj (eval closh.env/*closh-environment-init*))
  (let [result #?(:cljs (handle-line cmd execute-command-text)
                  :clj (wait-for-pipeline (eval (read-sh (string-push-back-reader cmd)))))]
    (cond
      (process/process? result)
      (process/exit (process/exit-code result))

      (and (seq? result)
           (every? #(process/process? %) result))
      (process/exit (process/exit-code (last result)))

      :else
      (print (str result)))))
