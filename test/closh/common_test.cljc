(ns closh.common-test
  (:require [clojure.test :refer [deftest testing is are]]
            [closh.zero.builtin :refer [getenv setenv]]
            [closh.zero.env]
            [closh.zero.core :refer [expand expand-alias expand-abbreviation]]
            [closh.zero.macros #?(:clj :refer :cljs :refer-macros) [defalias defabbr]]))

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

(deftest test-expansion-glob
  (testing "general matching"
    (are [pattern matches] (= matches (expand pattern))
      "./test/resources/foo.?ar"     ["./test/resources/foo.bar"]
      "./test/resources/foo.*r"      ["./test/resources/foo.bar"]
      "./test/resources/foo.*"       ["./test/resources/foo.bar","./test/resources/foo.bas","./test/resources/foo.baz"]
      "./test/resources/foo*"        ["./test/resources/foo.bar","./test/resources/foo.bas","./test/resources/foo.baz"]
      "./test/resources/*.{bar,baz}" ["./test/resources/foo.bar","./test/resources/foo.baz"]
      "./test/resources/*.b?[rs]"    ["./test/resources/foo.bar","./test/resources/foo.bas"]
      "./test/resources/*.b?[a-z]"   ["./test/resources/foo.bar","./test/resources/foo.bas","./test/resources/foo.baz"]
      "./test/resources/*"           ["./test/resources/foo.bar","./test/resources/foo.bas","./test/resources/foo.baz"]
      "./test/re*/*"                 ["./test/resources/foo.bar","./test/resources/foo.bas","./test/resources/foo.baz"]
      "./t*?[rt]/re*/*"              ["./test/resources/foo.bar","./test/resources/foo.bas","./test/resources/foo.baz"]))

  (testing "dotfiles specified explicitly"
    (are [pattern matches] (= matches (expand pattern))
      "./test/resources/.f*"        ["./test/resources/.foo.bar"]
      "./test/resources/.?oo.bar"   ["./test/resources/.foo.bar"]
      "./test/resources/.foo.bar"   ["./test/resources/.foo.bar"]))

  (testing "does not match any"
    (are [pattern matches] (= matches (expand pattern))
      "./test/resources/boo!*" ["./test/resources/boo!*"]))

  (is (= ["./doc/*/../package.js*"]
         (expand "./doc/*/../package.js*"))))

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
