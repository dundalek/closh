(ns closh.common-test
  (:require [clojure.test :refer [deftest is are]]
            [closh.builtin :refer [getenv setenv]]
            [closh.env]
            [closh.core :refer [expand expand-alias expand-abbreviation]]
            [closh.zero.platform.process :refer [shx]]
            [closh.macros :refer-macros [sh sh-str defalias defabbr]
                          :refer [sh sh-str defalias defabbr]]))

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

(deftest test-expansions
  (is (= (getenv "USER") (first (expand "$USER"))))

  (is (= "package.json" (first (expand "package.js*"))))

  (is (= "./package.json" (first (expand "./package.js*")))))

(deftest aliases
  (is (= "ls --color=auto" (expand-alias {"ls" "ls --color=auto"} "ls")))
  (is (= " ls --color=auto" (expand-alias {"ls" "ls --color=auto"} " ls")))
  (is (= "ls --color=auto -l" (expand-alias {"ls" "ls --color=auto"} "ls -l")))
  (is (= "lshw" (expand-alias {"ls" "ls --color=auto"} "lshw")))

  (is (= "my alias expansion" (do (defalias myalias "my alias expansion")
                                  (expand-alias "myalias"))))
  (is (= "my str alias" (do (defalias "myalias2" "my str alias")
                            (expand-alias "myalias2")))))

(deftest abbreviations
  (is (= "ls --color=auto" (expand-abbreviation {"ls" "ls --color=auto"} "ls")))
  (is (= " ls --color=auto" (expand-abbreviation {"ls" "ls --color=auto"} " ls")))
  (is (= "ls -l" (expand-abbreviation {"ls" "ls --color=auto"} "ls -l")))
  (is (= "lshw" (expand-abbreviation {"ls" "ls --color=auto"} "lshw")))

  (is (= "my abbr expansion" (do (defabbr myabbr "my abbr expansion")
                                 (expand-abbreviation "myabbr"))))
  (is (= "my str abbr" (do (defabbr "myabbr2" "my str abbr")
                           (expand-abbreviation "myabbr2")))))
