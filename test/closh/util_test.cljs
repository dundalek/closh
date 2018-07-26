(ns closh.util-test
  (:require [clojure.test :refer [deftest testing is are run-tests]]
            [closh.zero.platform.process :refer [getenv]]
            [closh.util :refer [source-shell]]))

(deftest test-source-shell

  (is (= nil (source-shell "export A=42")))
  (is (= "42" (getenv "A")))

  (source-shell "A=84")
  (is (= "84" (getenv "A")))

  (source-shell "unset A")
  (is (= nil (getenv "A")))

  (source-shell "export A='forty two'")
  (is (= "forty two" (getenv "A")))

  (source-shell "export A='forty\ntwo'")
  (is (= "forty\ntwo" (getenv "A"))))
