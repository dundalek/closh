(ns closh.reader-test
  (:require [clojure.test :refer [deftest testing is are run-tests]]
            [clojure.tools.reader.reader-types :refer [string-push-back-reader]]
            [closh.reader]))

(deftest test-reader

  (are [x y] (= x (closh.reader/read (string-push-back-reader y)))

    (list 'ping (symbol "8.8.8.8"))
    "ping 8.8.8.8"

    (list 'ls (symbol "*.{cljc,clj}"))
    "ls *.{cljc,clj}"

    (list 'vim (symbol "~/.closhrc"))
    "vim ~/.closhrc"

    (list 'git 'clone (symbol "git@github.com:dundalek/closh.git"))
    "git clone git@github.com:dundalek/closh.git"

    '(echo $USER/$DISPLAY)
    "echo $USER/$DISPLAY"

    '(echo (+ 2 3))
    "echo (+ 2 3)"

    '(echo hi | cat)
    "echo hi | cat"

    '(echo 2 > tmp)
    "echo 2 > tmp"

    '((+ 1 2))
    "(+ 1 2)")


  (are [x] (thrown? #?(:clj Exception :cljs js/Error) (closh.reader/read (string-push-back-reader x)))

    "echo (str 8.8.8)"

    "echo \""

    "echo (+ 1"))
