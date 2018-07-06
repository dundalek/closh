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

(def child-process (js/require "child_process"))

(defn -main []
  (closh.zero.platform.eval/execute-text
    (str (pr-str closh.env/*closh-environment-init*)))
  (let [cmd (-> (seq js/process.argv)
                (nth 6))
        result (handle-line cmd execute-command-text)]
    (cond
      (instance? child-process.ChildProcess result)
      (process/exit (process/exit-code result))

      (and (seq? result)
           (every? #(instance? child-process.ChildProcess %) result))
      (process/exit (process/exit-code (last result)))

      :else
      (.write js/process.stdout (str result)))))

(set! *main-cli-fn* -main)
