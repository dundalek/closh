(ns closh.test-util.runner
  (:require [clojure.test :refer [run-tests]]
            [closh.reader-test]
            [closh.compiler-test]
            [closh.process-test]
            [closh.pipeline-test]
            [closh.common-test]
            [closh.core-test]))

(def report-orig clojure.test/report)

(defn report-custom [& args]
  ; (clojure.test/with-test-out (println args))
  (apply report-orig args))

(defn -main[]
  (binding [clojure.test/report report-custom]
    (time
      (run-tests
       'closh.reader-test
       'closh.compiler-test
       'closh.common-test
       'closh.pipeline-test
       'closh.process-test
       'closh.core-test))))
