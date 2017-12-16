(ns closh.reader-test
  (:require [cljs.test :refer-macros [deftest testing is are run-tests]]
            [closh.reader :refer [read-string]]))

(deftest test-append-commpletion

  (are [x y] (= x (read-string y))

    '("ping" "8.8.8.8")
    "ping 8.8.8.8"

    '("ls" "*.{cljc,clj}")
    "ls *.{cljc,clj}"

    '("vim" "~/.closhrc")
    "vim ~/.closhrc"

    '("git" "clone" "git@github.com:dundalek/closh.git")
    "git clone git@github.com:dundalek/closh.git"

    '("echo" "$USER/$DISPLAY")
    "echo $USER/$DISPLAY"

    '("echo" (+ 2 3))
    "echo (+ 2 3)"))
