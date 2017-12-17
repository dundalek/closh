(ns closh.test-util.spawn-helper
  (:require [clojure.string]
            [closh.parser]
            [closh.builtin]
            [closh.eval :refer [execute-command-text]]
            [closh.core :refer [handle-line]])
  (:require-macros [closh.core :refer [sh]]))

(def child-process (js/require "child_process"))

(defn -main []
  (let [cmd (-> (seq js/process.argv)
                (nth 5))
        result (handle-line cmd execute-command-text)]
    (cond
      (instance? child-process.ChildProcess result)
      (js/process.exit (.-exitCode result))

      (and (seq? result)
           (every? #(instance? child-process.ChildProcess %) result))
      (js/process.exit (.-exitCode (last result)))

      :else
      (.write js/process.stdout (str result)))))

(set! *main-cli-fn* -main)
