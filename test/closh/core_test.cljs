(ns closh.core-test
  (:require [cljs.test :refer-macros [deftest testing is are]]
            [clojure.spec.alpha :as s]
            [clojure.string]
            [closh.parser :refer [parse]]
            [closh.core :refer [shx expand expand-command process-output line-seq pipe pipe-multi pipe-map pipe-filter]
                        :refer-macros [sh]]))

(def child-process (js/require "child_process"))

(defn bash [cmd]
  (let [proc (.spawnSync child-process
                         "bash"
                         #js["-c" cmd]
                         #js{:encoding "utf-8"})]
    {:stdout (.-stdout proc)
     :stderr (.-stderr proc)
     :code (.-status proc)}))

(defn closh [cmd]
  (let [proc (.spawnSync child-process
                         "lumo"
                         #js["--classpath" "src" "test/closh/tester.cljs" cmd]
                         #js{:encoding "utf-8"})]
    {:stdout (.-stdout proc)
     :stderr (.-stderr proc)
     :code (.-status proc)}))

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

    '(-> (cd (expand "dirname")))
    '(cd dirname))


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

  (is (= '(shx "ls" [(expand "-l")])
         (macroexpand '(sh ls -l))))

  (are [x y] (= x (:stdout (closh y)))
    "3\n"
    "(+ 1 2)"

    "hi\n"
    "echo hi"

    "hi\n"
    "echo hi | (str)"

    "HI\n"
    "echo hi | (clojure.string/upper-case)"

    "HI\n"
    "echo hi | (str/upper-case)"

    "3\n"
    "echo (+ 1 2)"

    "x\n"
    "echo (sh-str echo x)"

    "3\n"
    "(list :a :b :c) | (count)"

    "OK\n"
    "(identity true) && echo OK"

    "false\n"
    "(identity false) && echo OK"

    "OK\n"
    "(identity false) || echo OK")

  (are [x] (= (bash x) (closh x))
    "ls"

    "git status"

    "ls -l *.json"

    "ls $HOME"

    "ls | head"

    "echo hi && echo OK"

    "! echo hi && echo NO"

    "echo hi || echo NO"

    "! echo hi || echo OK"

    "echo a && echo b && echo c"

    "echo a | egrep b || echo OK")

  (are [x y] (= (bash x) (closh y))
    "ls | head -n 5"
    "ls |> (take 5)"

    "ls | tail -n 5"
    "ls |> (take-last 5)"

    "ls | tail -n +5"
    "ls |> (drop 4)"

    "ls -a | grep \"^\\.\""
    "ls -a |> (filter #(re-find #\"^\\.\" %))"

    "ls | sed -n 1~2p"
    "ls |> (keep-indexed #(when (odd? (inc %1)) %2))"

    "ls | sort -r | head -n 5"
    "ls |> (reverse) | (take 5)"

    "ls *.json | sed 's/\\.json$/.txt/'"
    "ls | #(str/replace % #\"\\.txt\" \".md\""

    "echo $(date \"+%Y-%m-%d\")"
    "echo (sh-str date \"+%Y-%m-%d\")"

    "result=`echo '(1 + sqrt(5))/2' | bc -l`; echo \\\"${result:0:10}\\\""
    "(-> (/ (+ 1 (Math.sqrt 5)) 2) str (subs 0 10))"))
