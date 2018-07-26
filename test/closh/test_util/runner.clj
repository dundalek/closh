(ns closh.test-util.runner
  (:require [clojure.test :refer [run-tests]]
            [closh.compiler-test]
            [closh.process-test]
            [closh.common-test]
            [closh.pipeline-test]
            ; [closh.completion-test]
            [closh.core-test]
            [closh.reader-test]
            [closh.util-test]))

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
       'closh.process-test
       'closh.common-test
       'closh.pipeline-test
       ; 'closh.completion-test
       'closh.core-test
       'closh.util-test))))
