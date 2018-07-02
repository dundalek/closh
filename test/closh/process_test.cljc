(ns closh.process-test
  (:require [cljs.test :refer-macros [deftest is are]]
            [closh.zero.pipeline :refer [process-output]]
            [closh.core
             :refer [shx]
             :refer-macros [sh sh-str defalias defabbr]]))

(deftest run-test

  (is (= "5\n" (process-output (shx "echo" [(+ 2 3)]))))

  (is (= "" (-> (shx "ls" [] {:redir [[:out 1 "/dev/null"]]})
                process-output))))
