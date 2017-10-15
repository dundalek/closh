(ns closh.core-test
  (:require [cljs.test :refer-macros [deftest testing is are]]
            [clojure.spec.alpha :as s]
            [closh.parser]))
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

       '(-> (shx "ls") (pipe-multi (reverse)) (pipe (shx "head")))
       '(ls |> (reverse) | head)

       '(-> (do (list 1 2 3) (reverse)))
       '((list 1 2 3) (reverse))))
