(ns closh.reader-test
  (:require [cljs.test :refer-macros [deftest testing is are run-tests]]
            [closh.reader :refer [read-string]]))

(deftest test-reader

  (are [x y] (= x (read-string y))

    (list 'sh 'ping (symbol "8.8.8.8"))
    "ping 8.8.8.8"

    (list 'sh 'ls (symbol "*.{cljc,clj}"))
    "ls *.{cljc,clj}"

    (list 'sh 'vim (symbol "~/.closhrc"))
    "vim ~/.closhrc"

    (list 'sh 'git 'clone (symbol "git@github.com:dundalek/closh.git"))
    "git clone git@github.com:dundalek/closh.git"

    '(sh echo $USER/$DISPLAY)
    "echo $USER/$DISPLAY"

    '(sh echo (+ 2 3))
    "echo (+ 2 3)"

    '(sh echo hi | cat)
    "echo hi | cat"

    '(sh echo 2 > tmp)
    "echo 2 > tmp"))
