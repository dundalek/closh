(ns closh.test-util.spawn-helper
  (:require [clojure.string]
            [closh.parser]
            [closh.compiler]
            [closh.builtin]
            [closh.env]
            [closh.zero.platform.eval :refer [execute-command-text]]
            [closh.core :refer [handle-line]]
            [closh.zero.platform.process :as process]
            [closh.macros :refer-macros [sh]]))

(defn -main []
  (closh.zero.platform.eval/execute-text
    (str (pr-str closh.env/*closh-environment-init*)))
  (let [cmd (-> (seq js/process.argv)
                (nth 7))
        result (handle-line cmd execute-command-text)]
    (cond
      (process/process? result)
      (process/exit (process/exit-code result))

      (and (seq? result)
           (every? #(process/process? %) result))
      (process/exit (process/exit-code (last result)))

      :else
      (print (str result)))))
