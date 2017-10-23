(ns closh.tester
  (:require [clojure.tools.reader]
            [clojure.tools.reader.impl.commons]
            [closh.parser]
            [closh.builtin]
            [closh.eval :refer [execute-text]]
            [closh.core :refer [handle-line]])
  (:require-macros [alter-cljs.core :refer [alter-var-root]]
                   [closh.reader :refer [patch-reader]]
                   [closh.core :refer [sh]]))

(defn -main []
  (patch-reader)
  (let [cmd (-> (seq js/process.argv)
                (nth 5))
        proc (handle-line cmd execute-text)]
    (if-let [code (and proc (.-exitCode proc))]
      (js/process.exit code))))

(set! *main-cli-fn* -main)
