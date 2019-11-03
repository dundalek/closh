(ns closh.reader-test
  (:require [clojure.test :refer [deftest testing is are run-tests]]
            [closh.zero.reader :as reader]))

(deftest test-reader

  (are [x y] (= x (reader/read-string y))

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

    '(echo "a\nb")
    "echo \"a\nb\""

    '(echo 3)
    "echo 3"

    ; (list 'echo false)
    ; "echo false"
    ;
    ; (list 'echo nil)
    ; "echo nil"

    '((+ 1 2))
    "(+ 1 2)")

  (are [x y] (= x (reader/read-string-all y))

    '((ls)
      (echo x)
      ((+ 1 2)))
    "ls\necho x\n(+ 1 2)"

    '((echo a)
      (echo b))
    "echo a\\;echo b"

    '((echo a)
      (echo b))
    "echo a \\;echo b"

    '((echo a)
      (echo b))
    "echo a\\; echo b"

    '((echo a)
      (echo b))
    "echo a \\; echo b"

    '((echo a))
    "echo a ; echo b"

    '((echo a)
      (echo b))
    "\\;echo a\\;echo b\\;"

    '((echo a)
      (b))
    "echo a \nb"

    '((echo a b))
    "echo a \\\nb"

    '((echo a | (clojure.string/upper-case)))
    "echo a \\\n | (clojure.string/upper-case)"

    '((echo a)
      (echo b))
    "\n\necho a\n\n\necho b\n\n")

    ;; (list (list 'ls (symbol "A Filename With Spaces")))
    ;; "ls A\\ Filename\\ With\\ Spaces")

    ;; Maybe allow trailing pipe without backslash escape?
    ;; '((echo a | (clojure.string/upper-case)))
    ;; "echo a |\n (clojure.string/upper-case)")

  (are [x] (thrown? #?(:clj Exception :cljs js/Error) (reader/read-string x))

    "echo (str 8.8.8)"

    "echo \""

    "echo (+ 1"))

(deftest test-reader-forms
  (is (= '[(closh.zero.macros/sh (+ 1 2))
           (closh.zero.macros/sh (* 3 4))]
         (let [in (reader/string-reader "(+ 1 2)\n(* 3 4)")]
           [(reader/read-sh in)
            (reader/read-sh in)])))

  (is (= '[(closh.zero.macros/sh echo a b)
           (closh.zero.macros/sh ls)]
         (let [in (reader/string-reader "echo a b\nls")]
           [(reader/read-sh in)
            (reader/read-sh in)])))

  (is (= '[(closh.zero.macros/sh (+ 1 2))]
         (let [in (reader/string-reader "(+ 1 2)\n")]
           [(reader/read-sh in)])))

  (is (= '[(closh.zero.macros/sh (+ 1 2))]
         (let [in (reader/string-reader "(+ 1 2)")]
           [(reader/read-sh in)]))))
