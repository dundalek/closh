(ns closh.main
  (:require [clojure.tools.reader]
            [clojure.tools.reader.impl.commons]
            [closh.eval :refer [eval-cljs]]
            [closh.core :refer [get-out-stream wait-for-process wait-for-event handle-line]])
  (:require-macros [alter-cljs.core :refer [alter-var-root]]
                   [closh.reader :refer [patch-reader]]))
                  ;  [closh.core :refer [sh]]))

(def readline (js/require "readline"))

(defn -main []
  (patch-reader)
  (let [rl (.createInterface readline
             #js{:input js/process.stdin
                 :output js/process.stdout
                 :prompt "$ "})]
    (-> rl
      (.on "line" #(do (handle-line % eval-cljs)
                       (.prompt rl)))
      (.on "close" #(.exit js/process 0)))
    (.prompt rl)))

(set! *main-cli-fn* -main)
