(ns closh.compiler-test
  (:require [clojure.test :refer [deftest is are]]
            [closh.zero.parser]
            [closh.zero.compiler]))

(deftest compiler-test

  (are [x y] (= (macroexpand-1 x) (closh.zero.compiler/compile-batch (closh.zero.parser/parse y)))
    '(-> (shx "ls" [(expand "-l")]))
    '(ls -l)

    '(-> (shx "ls" [(expand-partial "-l")]))
    '(ls "-l")

    '(-> (shx "ls" [(expand ".")]))
    '(ls .)

    '(-> (do (list 1 2 3) (reverse)))
    '((list 1 2 3) (reverse))

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
    '(cd dirname)

    ;; === Expansion coercion tests ===

    '(-> (shx "echo" [[2]]))
    '(echo 2)

    '(-> (shx "echo" [[false]]))
    '(echo false)

    '(-> (shx "echo" [[[1 2 3]]]))
    '(echo [1 2 3])

    '(-> (shx "echo" [[(+ 1 2)]]))
    '(echo (+ 1 2))

     #?(:clj '(-> (clojure.core/apply exit (clojure.core/concat [1] (expand-partial "abc"))))
        :cljs '(-> (cljs.core/apply exit (cljs.core/concat [1] (expand-partial "abc")))))
    '(exit 1 "abc"))

  (is (=
        '(do (shx "echo" [(expand "a")]) (shx "echo" [(expand "b")]))
        (closh.zero.compiler/compile-batch (closh.zero.parser/parse '(echo a \; echo b))))))
