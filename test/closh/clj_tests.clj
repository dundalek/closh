(ns closh.clj-tests
  (:require [clojure.test :refer [deftest is]]))

(deftest add-1-to-1
  (is (= 2 (+ 1 1))))
