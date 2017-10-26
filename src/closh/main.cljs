(ns closh.main
  (:require [clojure.tools.reader]
            [clojure.tools.reader.impl.commons]
            [clojure.pprint :refer [pprint]]
            [clojure.string]
            ; [lumo.io]
            [closh.parser]
            [closh.builtin]
            [closh.eval :refer [execute-text]]
            [closh.core :refer [handle-line]])
  (:require-macros [alter-cljs.core :refer [alter-var-root]]
                   [closh.reader :refer [patch-reader]]
                   [closh.core :refer [sh]]))

(enable-console-print!)

(def ^:no-doc readline (js/require "readline"))
(def ^:no-doc child-process (js/require "child_process"))

(defn -main
  "Start closh REPL with prompt and readline."
  []
  (patch-reader)
  (let [rl (.createInterface readline
             #js{:input js/process.stdin
                 :output js/process.stdout
                 :prompt "$ "})]
    (doto rl
      (.on "line"
        (fn [input]
          (when (not (clojure.string/blank? input))
            (let [result (handle-line input execute-text)]
              (when-not (or (nil? result)
                            (instance? child-process.ChildProcess result))
                (.write js/process.stdout (with-out-str (pprint result))))))
          (.prompt rl)))
      (.on "close" #(.exit js/process 0))
      (.prompt rl))))

(set! *main-cli-fn* -main)
