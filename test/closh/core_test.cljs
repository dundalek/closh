(ns closh.core-test
  (:require [cljs.test :refer-macros [deftest testing is are]]
            [clojure.spec.alpha :as s]
            [closh.parser]
            [closh.core :refer [shx expand expand-command process-output]]))
  ; (:require-macros [closh.core :refer [sh shh]]))

;
; ; todo Invalid number handling:
; (s/conform ::cmd-list '(echo x 2> tmp.txt))
; (s/conform ::cmd-list '(echo hi 1>&2))

; (s/conform ::cmd-list '())

(defn parse [x]
  (closh.parser/process-command-list (s/conform :closh.parser/cmd-list x)))

(deftest run-test
  (are [x y] (= x (parse y))
    '(-> (shx "ls" (expand "-l")))
    '(ls -l)

    '(-> (shx "ls" (expand-partial "-l")))
    '(ls "-l")

    '(-> (shx "ls") (pipe-multi (reverse)) (pipe (shx "head")))
    '(ls |> (reverse) | head)

    '(-> (do (list 1 2 3) (reverse)))
    '((list 1 2 3) (reverse))

    '(-> (shx "echo" (expand-command (-> (shx "date")))))
    '(echo (sh date))

    '(-> (shx "echo" (+ 1 2)))
    '(echo (+ 1 2)))

  (is (= (list "a" "b")) (expand-command (shx "echo" "a b")))

  (is (= "3\n" (process-output (shx "echo" (+ 1 2)))))

  (is (= (.-USER js/process.env) (first (expand "$USER"))))

  (is (= "project.clj\n" (process-output (shx "ls" (expand "project*"))))))
