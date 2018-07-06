(ns closh.test-util.runner
  (:require [goog.object :as gobj]
            [clojure.test :refer [report run-tests]]
            [closh.compiler-test]
            [closh.process-test]
            [closh.common-test]
            [closh.pipeline-test]
            [closh.completion-test]
            [closh.core-test]
            [closh.reader-test]
            [closh.util-test]))

(defmethod report [:cljs.test/default :end-run-tests] [m]
  (if (cljs.test/successful? m)
    (gobj/set js/process "exitCode" 0)
    (gobj/set js/process "exitCode" 1)))

(defn -main []
  (time
   (run-tests
     'closh.reader-test
     'closh.compiler-test
     'closh.process-test
     'closh.common-test
     'closh.pipeline-test
     'closh.completion-test
     'closh.core-test
     'closh.util-test)))
