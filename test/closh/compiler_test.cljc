(ns closh.compiler-test
  (:require [clojure.test :refer [deftest are]]
            [closh.parser]
            [closh.compiler]))

(deftest compiler-test

  (are [x y] (= x (closh.compiler/compile-batch (closh.parser/parse y)))
    '(-> (shx "ls" [(expand "-l")]))
    '(ls -l)

    '(-> (shx "ls" [(expand-partial "-l")]))
    '(ls "-l")

    '(-> (shx "ls" [(expand ".")]))
    '(ls .)

    '(-> (shx "ls" []) (pipe-multi (partial reverse)) (pipe (shx "head" [])))
    '(ls |> (reverse) | head)

    '(-> (do (list 1 2 3) (reverse)))
    '((list 1 2 3) (reverse))

    '(-> (shx "ls" []) (pipe-multi (partial reverse)))
    '(ls |> (reverse))

    '(-> (shx "echo" [(expand "hi")]) (pipe (partial str)))
    '(echo hi | (str))

    '(-> (shx "ls" [] {:redir [[:out 1 (expand-redirect "dirlist")] [:set 2 1]]}))
    '(ls > dirlist 2 >& 1)

    '(-> (shx "ls" [] {:redir [[:set 2 1] [:out 1 (expand-redirect "dirlist")]]}))
    '(ls 2 >& 1 > dirlist)

    '(-> (shx "cat" [] {:redir [[:in 0 (expand-redirect "file.txt")]]}))
    '(cat < file.txt)

    '(-> (shx "cat" [] {:redir [[:in 3 (expand-redirect "file.txt")]]}))
    '(cat 3 < file.txt)

    '(-> (shx "ls" [] {:redir [[:out 1 (expand-redirect "file.txt")]]}))
    '(ls 1 > file.txt)

    '(-> (shx "ls" [] {:redir [[:append 1 (expand-redirect "file.txt")]]}))
    '(ls >> file.txt)
    ;
    '(-> (shx "ls" [] {:redir [[:out 1 (expand-redirect "file.txt")] [:set 2 1]]}))
    '(ls &> file.txt)

    '(-> (shx "ls" [] {:redir [[:append 1 (expand-redirect "file.txt")] [:set 2 1]]}))
    '(ls &>> file.txt)

    '(-> (shx "ls" [] {:redir [[:rw 0 (expand-redirect "file.txt")]]}))
    '(ls <> file.txt)

    '(-> (shx "ls" [] {:redir [[:rw 3 (expand-redirect "file.txt")]]}))
    '(ls 3 <> file.txt)

    '(-> (shx "wc" [(expand "-l")] {:redir [[:set 2 1]]}))
    '(wc -l 2 >& 1)

    #?(:clj '(-> (clojure.core/apply cd (clojure.core/concat (expand "dirname"))))
       :cljs '(-> (cljs.core/apply cd (cljs.core/concat (expand "dirname")))))
    '(cd dirname)))
