(ns closh.test-util.runner
  (:require [clojure.test :refer [run-tests]]
            [closh.clj-tests]))

(defn -main[]
  (time
    (run-tests
     'closh.clj-tests)))
