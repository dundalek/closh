(ns closh.test-util.runner
  (:require [clojure.test :refer [run-tests]]
            [closh.reader-test]
            [closh.compiler-test]
            [closh.process-test]
            [closh.common-test]))

(defn -main[]
  (time
    (run-tests
     'closh.reader-test
     'closh.compiler-test
     'closh.common-test
     'closh.process-test)))
