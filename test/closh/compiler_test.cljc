(ns closh.compiler-test
  (:require [clojure.test :refer [deftest is are]]
            [closh.zero.parser]
            [closh.zero.compiler]
            [closh.zero.builtin]
            [closh.zero.core :refer [shx expand expand-partial expand-redirect expand-command]]))

#_(comment
  `(apply ((deref closh.zero.env/*closh-commands*) (quote ~'cd)) (concat (closh.zero.core/expand "dirname")))
  (closh.zero.compiler/compile-batch (closh.zero.parser/parse '(cd dirname))))

(deftest compiler-test

  (are [x y] (= x (closh.zero.compiler/compile-batch (closh.zero.parser/parse y)))
    `(shx (expand-command "ls") [(expand "-l")])
    '(ls -l)

    `(shx (expand-command "ls") [(expand-partial "-l")])
    '(ls "-l")

    `(shx (expand-command "ls") [(expand ".")])
    '(ls .)

    '(do (list 1 2 3) (reverse))
    '((list 1 2 3) (reverse))

    `(shx (expand-command "ls") [] {:redir [[:out 1 (expand-redirect "dirlist")] [:set 2 1]]})
    '(ls > dirlist 2 >& 1)

    `(shx (expand-command "ls") [] {:redir [[:set 2 1] [:out 1 (expand-redirect "dirlist")]]})
    '(ls 2 >& 1 > dirlist)

    `(shx (expand-command "cat") [] {:redir [[:in 0 (expand-redirect "file.txt")]]})
    '(cat < file.txt)

    `(shx (expand-command "cat") [] {:redir [[:in 3 (expand-redirect "file.txt")]]})
    '(cat 3 < file.txt)

    `(shx (expand-command "ls") [] {:redir [[:out 1 (expand-redirect "file.txt")]]})
    '(ls 1 > file.txt)

    `(shx (expand-command "ls") [] {:redir [[:append 1 (expand-redirect "file.txt")]]})
    '(ls >> file.txt)

    `(shx (expand-command "ls") [] {:redir [[:out 1 (expand-redirect "file.txt")] [:set 2 1]]})
    '(ls &> file.txt)

    `(shx (expand-command "ls") [] {:redir [[:append 1 (expand-redirect "file.txt")] [:set 2 1]]})
    '(ls &>> file.txt)

    `(shx (expand-command "ls") [] {:redir [[:rw 0 (expand-redirect "file.txt")]]})
    '(ls <> file.txt)

    `(shx (expand-command "ls") [] {:redir [[:rw 3 (expand-redirect "file.txt")]]})
    '(ls 3 <> file.txt)

    `(shx (expand-command "wc") [(expand "-l")] {:redir [[:set 2 1]]})
    '(wc -l 2 >& 1)

    `(apply ((deref closh.zero.env/*closh-commands*) (quote ~'cd)) (concat (closh.zero.core/expand "dirname")))
    '(cd dirname)

    ;; === Expansion coercion tests ===

    `(shx (expand-command "echo") [[2]])
    '(echo 2)

    `(shx (expand-command "echo") [[false]])
    '(echo false)

    `(shx (expand-command "echo") [[[1 2 3]]])
    '(echo [1 2 3])

    `(shx (expand-command "echo") [[(~'+ 1 2)]])
    '(echo (+ 1 2))

    `(apply ((deref closh.zero.env/*closh-commands*) (quote ~'exit)) (concat [1] (closh.zero.core/expand-partial "abc")))
    '(exit 1 "abc"))

  (is (=
        `(do (closh.zero.pipeline/wait-when-process (shx (expand-command "echo") [(expand "a")]))
             (shx (expand-command "echo") [(expand "b")]))
        (closh.zero.compiler/compile-batch (closh.zero.parser/parse '(echo a \; echo b))))))
