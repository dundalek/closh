(ns closh.common-test
  (:require [clojure.test :refer [deftest is are]]
            [closh.builtin :refer [getenv setenv]]))

(deftest test-getenv-setenv

  (is (= {"A" "Test A" "B" "B Testing"}
         (do
           (setenv "A" "Test A")
           (setenv "B" "B Testing")
           (select-keys (getenv) ["A" "B"]))))

  (is (= '("forty two") (setenv "A" "forty two")))

  (is (= "forty\ntwo" (do (setenv "A" "forty\ntwo")
                          (getenv "A"))))

  (is (= '("1" "2") (setenv "ONE" "1" "TWO" "2")))
  (is (= {"ONE" "1", "TWO" "2"}
         (getenv "ONE" "TWO"))))
