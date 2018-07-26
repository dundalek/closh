(ns closh.util-test
  (:require [clojure.test :refer [deftest testing is are run-tests]]
            [closh.zero.platform.process :refer [getenv]]
            [closh.util :refer [source-shell]]))

(deftest test-source-shell

  (is (= nil (source-shell "export A=42")))
  (is (= "42" (getenv "A")))

  (is (= "84" (do (source-shell "A=84")
                  (getenv "A"))))

  (is (= nil (do (source-shell "unset A")
                 (getenv "A"))))

  (is (= "forty two" (do (source-shell "export A='forty two'")
                         (getenv "A"))))

  (is (= "forty\ntwo" (do (source-shell "export A='forty\ntwo'")
                          (getenv "A")))))
