(ns closh.util-test
  (:require [cljs.test :refer-macros [deftest testing is are run-tests]]
            [goog.object :as gobj]
            [closh.util :refer [source-shell]]))

(deftest test-source-shell

  (is (= nil (source-shell "export A=42")))
  (is (= "42" (gobj/get js/process.env "A")))

  (source-shell "A=84")
  (is (= "84" (gobj/get js/process.env "A")))

  (source-shell "unset A")
  (is (= nil (gobj/get js/process.env "A")))

  (source-shell "export A='forty two'")
  (is (= "forty two" (gobj/get js/process.env "A")))

  (source-shell "export A='forty
two'")
  (is (= "forty\ntwo" (gobj/get js/process.env "A"))))
