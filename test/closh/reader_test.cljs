(ns closh.reader-test
  (:require [cljs.test :refer-macros [deftest testing is are run-tests]]
            [cljs.tools.reader.reader-types :refer [string-push-back-reader]]
            [closh.reader :refer [read]]))

(deftest test-reader

  (are [x y] (= x (read (string-push-back-reader y)))

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
    "echo 2 > tmp")

  (is (thrown? js/Error (read-string "echo (str 8.8.8)")))
  (is (thrown? js/Error (read-string "echo \"")))
  (is (thrown? js/Error (read-string "echo (+ 1"))))
