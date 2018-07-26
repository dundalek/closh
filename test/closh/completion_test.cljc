(ns closh.completion-test
  (:require [clojure.test :refer [deftest testing is are run-tests]]
            [closh.completion :refer [append-completion]]))

(deftest test-append-commpletion

  (are [result line completion] (= result (append-completion line completion))

    "ls abc" "ls " "abc"

    "ls abc" "ls abc" "abc"

    "ls " "ls " ""

    "ls Abc" "ls ab" "Abc"

    "ls abc" "ls A" "abc"))
