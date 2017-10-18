(ns closh.tester
  (:require [clojure.tools.reader]
            [clojure.tools.reader.impl.commons]
            [closh.eval :refer [eval-cljs]]
            [closh.core :refer [get-out-stream wait-for-process wait-for-event handle-line]])
  (:require-macros [alter-cljs.core :refer [alter-var-root]]
                   [closh.reader :refer [patch-reader]]))


(defn -main []
  (patch-reader)
  (let [cmd (-> (seq js/process.argv)
                (nth 5))]
    (handle-line cmd eval-cljs)))

(set! *main-cli-fn* -main)
