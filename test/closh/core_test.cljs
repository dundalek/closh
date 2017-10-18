(ns closh.core-test
  (:require [cljs.test :refer-macros [deftest testing is are]]
            [clojure.spec.alpha :as s]
            [clojure.string]
            [closh.parser]
            [closh.core :refer [shx expand expand-command process-output line-seq pipe pipe-multi pipe-map pipe-filter]]))

(def child-process (js/require "child_process"))

(defn bash [cmd]
  (let [proc (.spawnSync child-process
                         "bash"
                         #js["-c" cmd]
                         #js{:encoding "utf-8"})]
    {:stdout (.-stdout proc)
     :stderr (.-stdout proc)
     :code (.-status proc)}))

(defn closh [cmd]
  (let [proc (.spawnSync child-process
                         "lumo"
                         #js["--classpath" "src" "test/closh/tester.cljs" cmd]
                         #js{:encoding "utf-8"})]
    {:stdout (.-stdout proc)
     :stderr (.-stdout proc)
     :code (.-status proc)}))


(defn parse [x]
  (closh.parser/process-command-list (s/conform :closh.parser/cmd-list x)))

(deftest run-test
  (are [x y] (= x (parse y))
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

    '(-> (shx "echo" [(expand-command (-> (shx "date" [])))]))
    '(echo (sh date))

    '(-> (shx "echo" [(+ 2 3)]))
    '(echo (+ 2 3))

    '(-> (shx "ls" []) (pipe-multi (partial reverse)))
    '(ls |> (reverse))

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
    '(ls 3 <> file.txt))

  ; (process-command-list (s/conform ::cmd-list '(echo x > tmp.txt)))
  ; (process-command-list (s/conform ::cmd-list '(echo x 2 > tmp.txt)))
  ; (process-command-list (s/conform ::cmd-list '(echo x >> tmp.txt)))

  (is (= (list "a" "b")) (expand-command (shx "echo" ["a b"])))

  (is (= "5\n" (process-output (shx "echo" [(+ 2 3)]))))

  (is (= (.-USER js/process.env) (first (expand "$USER"))))

  (is (= "project.clj\n" (process-output (shx "ls" [(expand "project*")]))))

  (is (= (-> (.readFileSync (js/require "fs") "package.json" "utf-8")
             (.trimRight)
             (.split "\n")
             (seq))
         (-> (line-seq (.createReadStream (js/require "fs") "package.json")))))

  (is (= (list "b" "a") (-> (shx "echo" ["a\nb"])
                            (pipe-multi (partial reverse)))))

  (is (= "B\nA\n") (-> (shx "echo" ["a\nb"])
                       (pipe-map clojure.string/upper-case)
                       (pipe (shx "sort" ["-r"]))
                       process-output))

  (is (= "2\n" (-> (shx "echo" ["a\nb"])
                   (pipe (shx "wc" ["-l"]))
                   process-output)))

  (is (= (list 3 2 1) (-> (list 1 2 3) (pipe reverse))))

  (is (= (list 1 3) (-> (list 1 2 3 4) (pipe-filter odd?))))

  (is (= "" (-> (shx "ls" [] {:redir [[:out 1 "/dev/null"]]})
                process-output)))

  ; '(echo hi 1 >& 2 | wc -l))
  (is (= "0\n" (-> (shx "echo" ["hix"] {:redir [[:out 2 "/dev/null"]
                                                [:set 1 2]]})
                   (pipe (shx "wc" ["-l"]))
                   process-output)))

  (is (= (bash "ls") (closh "ls"))))
; (process-command-list (s/conform ::cmd-list '(ls |> (map #(str/replace % #"\.txt" ".md")))))
; (process-command-list (s/conform ::cmd-list '(ls |> (map str/upper-case))))
; (process-command-list (s/conform ::cmd-list '(ls |> (reverse))))
; (process-command-list (s/conform ::cmd-list '(ls -a | grep "^\\.")))
; (process-command-list (s/conform ::cmd-list '(ls | (spit "files.txt"))))
;

  ; '(echo a | egrep (str "[0-9]+") || echo OK)
  ; '(echo hi && echo OK)
  ; '(! echo hi && echo NO)
  ; '(echo hi || echo NO)
  ; '(! echo hi || echo OK)
  ; '(echo a && echo b && echo c)
