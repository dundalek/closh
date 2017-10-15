(ns closh.core-test
  (:require [cljs.test :refer-macros [deftest testing is are]]
            [clojure.spec.alpha :as s]
            [clojure.string]
            [closh.parser]
            [closh.core :refer [shx expand expand-command process-output line-seq pipe pipe-multi pipe-map pipe-filter]]))
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

    '(-> (shx "ls" (expand ".")))
    '(ls .)

    '(-> (shx "ls") (pipe-multi (partial reverse)) (pipe (shx "head")))
    '(ls |> (reverse) | head)

    '(-> (do (list 1 2 3) (reverse)))
    '((list 1 2 3) (reverse))

    '(-> (shx "echo" (expand-command (-> (shx "date")))))
    '(echo (sh date))

    '(-> (shx "echo" (+ 1 2)))
    '(echo (+ 1 2))

    '(-> (shx "ls") (pipe-multi (partial reverse)))
    '(ls |> (reverse)))

  (is (= (list "a" "b")) (expand-command (shx "echo" "a b")))

  (is (= "3\n" (process-output (shx "echo" (+ 1 2)))))

  (is (= (.-USER js/process.env) (first (expand "$USER"))))

  (is (= "project.clj\n" (process-output (shx "ls" (expand "project*")))))

  (is (= (-> (.readFileSync (js/require "fs") "package.json" "utf-8")
             (.trimRight)
             (.split "\n")
             (seq))
         (-> (line-seq (.createReadStream (js/require "fs") "package.json")))))

  (is (= (list "b" "a") (-> (shx "echo" "a\nb")
                            (pipe-multi (partial reverse)))))

  (is (= "B\nA\n") (-> (shx "echo" "a\nb")
                       (pipe-map clojure.string/upper-case)
                       (pipe (shx "sort" "-r"))
                       process-output))

  (is (= "2\n" (-> (shx "echo" "a\nb")
                   (pipe (shx "wc" "-l"))
                   process-output)))

  (is (= (list 3 2 1) (-> (list 1 2 3) (pipe reverse))))

  (is (= (list 1 3) (-> (list 1 2 3 4) (pipe-filter odd?)))))



; (process-command-list (s/conform ::cmd-list '(ls |> (map #(str/replace % #"\.txt" ".md")))))
; (process-command-list (s/conform ::cmd-list '(ls |> (map str/upper-case))))
; (process-command-list (s/conform ::cmd-list '(ls -a | grep "^\\.")))
; (process-command-list (s/conform ::cmd-list '(ls | (spit "files.txt"))))
;

  ; '(echo a | egrep (str "[0-9]+") || echo OK)
  ; '(echo hi && echo OK)
  ; '(! echo hi && echo NO)
  ; '(echo hi || echo NO)
  ; '(! echo hi || echo OK)
  ; '(echo a && echo b && echo c)
