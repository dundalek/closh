(ns closh.core-test
  (:require [cljs.test :refer-macros [deftest testing is are run-tests]]
            [clojure.spec.alpha :as s]
            [clojure.string]
            [goog.object :as gobj]
            [closh.reader]
            [closh.util :refer [jsx->clj]]
            [closh.builtin :refer [getenv setenv]]
            [closh.env]
            [closh.eval :refer [execute-command-text]]
            [closh.zero.platform.io :refer [line-seq]]
            [closh.zero.pipeline :refer [process-output wait-for-pipeline pipe pipe-multi pipe-map pipe-filter pipeline-value pipeline-condition]]
            [closh.core
             :refer [shx expand expand-partial expand-alias expand-abbreviation]
             :refer-macros [sh sh-str defalias defabbr]]))

(def fs (js/require "fs"))
(def child-process (js/require "child_process"))
(def tmp (js/require "tmp"))

;; Clean up tmp files on unhandled exception
(tmp.setGracefulCleanup)

(defn bash [cmd]
  (let [proc (.spawnSync child-process
                         "bash"
                         #js["-c" cmd]
                         #js{:encoding "utf-8"})]
    {:stdout (.-stdout proc)
     :stderr (.-stderr proc)
     :code (.-status proc)}))

(defn closh-spawn [cmd]
  (let [proc (.spawnSync child-process
                         "lumo"
                         #js["-K" "--classpath" "src" "test/closh/test_util/spawn_helper.cljs" cmd]
                         #js{:encoding "utf-8"})]
    {:stdout (.-stdout proc)
     :stderr (.-stderr proc)
     :code (.-status proc)}))

(defn closh [cmd]
  (execute-command-text cmd closh.reader/read-sh-value))

(deftest run-test

  (is (= (.-USER js/process.env) (first (expand "$USER"))))

  (is (= "package.json\n" (process-output (shx "ls" [(expand "package.js*")]))))

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
                   (pipe (shx "awk" ["END {print NR}"]))
                   process-output)))

  (is (= (list 3 2 1) (-> (list 1 2 3) (pipe reverse))))

  (is (= (list 1 3) (-> (list 1 2 3 4) (pipe-filter odd?))))

  ; '(echo hi 1 >& 2 | wc -l))
  (is (= "0\n" (-> (shx "echo" ["hix"] {:redir [[:out 2 "/dev/null"]
                                                [:set 1 2]]})
                   (pipe (shx "awk" ["END {print NR}"]))
                   process-output)))

  (is (= '(shx "ls" [(expand "-l")] {:redir [[:set 0 :stdin] [:set 1 :stdout] [:set 2 :stderr]]})
         (macroexpand '(sh ls -l))))

  (are [x y] (= x (:stdout (closh y)))
    "3"
    "(+ 1 2)"

    "hi\n"
    "echo hi"

    "hi\n"
    "echo hi | (str)"

    "HI\n"
    "echo hi | (clojure.string/upper-case)"

    "HI\n"
    "echo hi | (st/upper-case)"

    "3\n"
    "echo (+ 1 2)"

    "x\n"
    "echo (sh-str echo x)"

    "3"
    "(list :a :b :c) |> (count)"

    "OK\n"
    "(identity true) && echo OK"

    "false"
    "(identity false) && echo OK"

    "OK\n"
    "(identity false) || echo OK"

    ; process to process - redirect stdout
    "ABC\n"
    "echo abc | tr \"[:lower:]\" \"[:upper:]\""

    ; process to fn - collect stdout
    "ABC\n"
    "echo abc | (clojure.string/upper-case)"

    ; process to sequence - split lines
    "(\"c\" \"b\" \"a\")"
    "printf \"a\\nb\\nc\" |> (reverse)"

    ; sequence to fn
    "1"
    "(list 1 2 3) | (first)"

    ; sequence to sequence
    "(3 2 1)"
    "(list 1 2 3) | (reverse)"

    ; sequence to process - join items
    "1\n2\n3\n"
    "(list 1 2 3) | cat -"

    "{:a 123}"
    "(identity {:a 123}) | cat -"

    "{:a 123}\n{:b 456}\n"
    "(list {:a 123} {:b 456}) | cat -"

    ; string to process
    "abc"
    "(str \"abc\") | cat -"

    ; string to sequence
    "(\"c\" \"b\" \"a\")"
    "(str \"a\\nb\\nc\") |> (reverse)"

    ; sequential
    "[1 2 3]"
    "(identity [1 2 3]) |> (identity)"

    "(1 2 3)"
    "(list 1 2 3) |> (identity)"

    ; non-seqable to seqable - wrap in list
    "(false)"
    "(identity false) |> (identity)"

    "[\"a\" \"b\"]"
    "echo a b | #(clojure.string/split % #\"\\s+\")"

    ; cmd helper to invoke command name by value
    "x\n"
    "(cmd \"echo\") x"

    "abc > /tmp/x\n"
    "echo abc '> /tmp/x")

  (are [x] (= (bash x) (closh x))
    "ls"

    "git status"

    "ls -l *.json"

    "ls $HOME"

    "ls | head"

    ; TODO: fix exit code
    ; "! echo hi && echo NO"

    "echo a | egrep b || echo OK"

    "cat < package.json"

    "echo x | cat < package.json"

    "cat < package.json | cat")

  (are [x y] (= (bash x) (closh y))
    "echo \"*\""
    "echo \"*\""

    "echo '$HOME $PWD'"
    "echo '\"$HOME $PWD\""

    "echo '$HOME'"
    "echo '$HOME"

    "ls | head -n 5"
    "ls |> (take 5) | cat"

    "ls | tail -n 5"
    "ls |> (take-last 5) | cat"

    "ls | tail -n +5"
    "ls |> (drop 4) | cat"

    "ls -a | grep \"^\\.\""
    "ls -a |> (filter #(re-find #\"^\\.\" %)) | cat"

    "ls | ls | awk 'NR%2==1'"
    "ls |> (keep-indexed #(when (odd? (inc %1)) %2)) | cat"

    "ls | sort -r | head -n 5"
    "ls |> (reverse) | (take 5) | cat"

    "ls *.json | sed 's/\\.json$/.txt/'"
    "ls *.json | #(clojure.string/replace % #\"\\.json\" \".txt\")"

    "ls *.json | sed 's/\\.json$/.txt/'"
    "ls *.json |> (map #(clojure.string/replace % #\"\\.json\" \".txt\")) | cat"

    "ls *.json | sed 's/\\.json$/.txt/'"
    "ls *.json | (fn [x] (clojure.string/replace x #\"\\.json\" \".txt\")) | cat"

    "ls *.json | sed 's/\\.json$/.txt/'"
    "ls *.json | ((fn [x] (clojure.string/replace x #\"\\.json\" \".txt\"))) | cat"

    "echo $(date \"+%Y-%m-%d\")"
    "echo (sh-str date \"+%Y-%m-%d\")"

    "result=`echo '(1 + sqrt(5))/2' | bc -l`; echo -n ${result:0:10}"
    "(-> (/ (+ 1 (Math.sqrt 5)) 2) str (subs 0 10))"

    "cat < package.json 2>/dev/null | cat"
    "cat < package.json 2 > /dev/null | cat"

    "for f in test/closh/*.cljs; do echo $f; cat $f; done"
    "ls test/closh/*.cljs |> (map #(str % \"\\n\" (sh-str cat (str %)))) | cat"

    "if test -f package.json; then echo file exists; else echo no file; fi"
    "echo (if (sh-ok test -f package.json) \"file exists\" \"no file\")"

    "if test -f asdfgh.json; then echo file exists; else echo no file; fi"
    "echo (if (sh-ok test -f asdfgh.json) \"file exists\" \"no file\")"

    "ls -l `echo *.json *.md`"
    "ls -l (sh-seq echo *.json *.md)"

    "bash -c \"echo err 1>&2; echo out\""
    "bash -c \"echo err 1>&2; echo out\"")

  (are [x y] (= x (pipeline-value y))
    ; process to process - redirect stdout
    "ABC\n"
    ; "echo abc | tr \"[:lower:]\" \"[:upper:]\""
    (-> (shx "echo" ["abc"])
        (pipe (shx "tr" ["[:lower:]" "[:upper:]"])))

    ; process to fn - collect stdout
    "ABC\n"
    ; "echo abc | (clojure.string/upper-case)"))
    (-> (shx "echo" ["abc"])
        (pipe clojure.string/upper-case))

    ; process to sequence - split lines
    '("c" "b" "a")
    ; "echo -e \"a\\nb\\nc\" |> (reverse)"
    (-> (shx "printf" ["a\\nb\\nc"])
        (pipe-multi reverse))

    ; ; sequence to fn
    1
    ; "(list 1 2 3) | (first)"
    (-> (list 1 2 3)
        (pipe first))

    ; sequence to sequence
    '(3 2 1)
    ; "(list 1 2 3) | (reverse)"))
    (-> (list 1 2 3)
        (pipe reverse))

    ; sequence to process - join items
    "1\n2\n3\n"
    ; "(list 1 2 3) | cat -"
    (-> (list 1 2 3)
        (pipe (shx "cat" ["-"])))

    ; ; sequence of sequences could be tab separated?
    ; "1\t2\n3\t4\n"
    ; ; "(list [1 2] [3 4]) | cat -"
    ; (pipe (list [1 2] [3 4]) (shx "cat"))

    "{:a 123}"
    ; "(identity {:a 123}) | cat -"
    (pipe {:a 123} (shx "cat"))

    "{:a 123}\n{:b 456}\n"
    ; "(list {:a 123} {:b 456}) | cat -"
    (pipe (list {:a 123} {:b 456}) (shx "cat"))

    ; string to process
    "abc"
    ; "(str \"abc\") | cat -"
    (pipe "abc" (shx "cat" ["-"]))

    "abc\n"
    (pipe (shx "echo" ["abc"]) (shx "cat" ["-"]))

    ; string to sequence
    '("c" "b" "a")
    ; "(str \"a\\nb\\nc\") |> (reverse)"
    (pipe-multi "a\nb\nc" reverse)

    ; seqable to sequence
    '[1 2 3]
    ; "(identity [1 2 3] |> (identity)"
    (pipe-multi [1 2 3] identity)

    ; non-seqable to seqable - wrap in list
    '(false)
    ; "(identity false) |> (identity)"))
    (pipe-multi false identity))

  (are [x y] (= x
                (let [file (tmp.fileSync)
                      f (.-name file)
                      result (closh y)
                      out (fs.readFileSync f "utf-8")]
                  (.removeCallback file)
                  out))

    "x1\n"
    (str "echo x1 > " f)

    ; TODO: enable spit - there is a problem with test runner when lumo.io is required
    ; "x2\n"
    ; (str "echo x2 | (spit \"" f "\")")

    "x3\ny1\n"
    (str "(sh echo x3 > \"" f "\") (sh echo y1 >> \"" f "\")")

    ""
    (str "echo x4 2 > " f)))

(when (not= js/process.env.NODE_ENV "development")
  (deftest run-special-cases
    (are [x y] (= (bash x) (closh-spawn y))
      "echo hi && echo OK"
      "echo hi && echo OK"

      "echo hi || echo NO"
      "echo hi || echo NO"

      "! echo hi || echo OK"
      "! echo hi || echo OK"

      "echo a && echo b && echo c"
      "echo a && echo b && echo c"

      "mkdir x/y/z || echo FAILED"
      "mkdir x/y/z || echo FAILED"

      "for f in test/closh/*.cljs; do echo $f; cat $f; done"
      "ls test/closh/*.cljs |> (map #(do (sh echo (str %)) (sh cat (str %))))"

      "if test -f package.json; then echo file exists; else echo no file; fi"
      "(if (sh-ok test -f package.json) (sh echo file exists) (sh echo no file))"

      "ls; echo hi"
      "(sh ls) (sh echo hi)"

      "echo x 1>&2"
      "echo x 1 >& 2"

      "bash -c \"echo err 1>&2; echo out\" 2>&1"
      "bash -c \"echo err 1>&2; echo out\" 2 >& 1")

      ; TODO: fix stderr redirects
      ; (bash"bash -c \"echo err 1>&2; echo out\" 2>&1 | cat")
      ; "bash -c \"echo err 1>&2; echo out\" 2 >& 1 | cat")

    (is (= {:stdout "x\n" :stderr "" :code 0}
           (closh-spawn "(sh (cmd (str \"ec\" \"ho\")) x)")))

    (is (= "_asdfghj_: command not found\n"
           (:stderr (closh-spawn "_asdfghj_"))))

    (is (= {:stderr "_asdfghj_: command not found\n"
            :stdout ""}
           (-> (closh-spawn "_asdfghj_ && echo NO")
               (select-keys [:stdout :stderr]))))

    (is (= {:stderr "_asdfghj_: command not found\n"
            :stdout "YES\n"}
           (-> (closh-spawn "_asdfghj_ || echo YES")
               (select-keys [:stdout :stderr]))))))

(deftest test-builtin-getenv-setenv

  (is (= (jsx->clj js/process.env) (getenv)))

  (is (= '("forty two") (setenv "A" "forty two")))
  (is (= (gobj/get js/process.env "A") (getenv "A")))

  (is (= "forty\ntwo" (do (setenv "A" "forty\ntwo")
                          (getenv "A"))))

  (is (= '("1" "2") (setenv "ONE" "1" "TWO" "2")))
  (is (= {"ONE" "1", "TWO" "2"}
         (getenv "ONE" "TWO")))

  (is (= (pr-str (setenv "ONE" "6")) (:stdout (closh "setenv \"ONE\" \"6\""))))
  (is (= "42") (:stdout (closh "(sh setenv ONE 42) (sh getenv ONE)")))
  (is (= "42") (:stdout (closh "(sh setenv \"ONE\" \"42\") (sh getenv \"ONE\")")))
  (is (= (getenv "ONE") (:stdout (closh "getenv \"ONE\"")))))

(deftest aliases
  (is (= "ls --color=auto" (expand-alias {"ls" "ls --color=auto"} "ls")))
  (is (= " ls --color=auto" (expand-alias {"ls" "ls --color=auto"} " ls")))
  (is (= "ls --color=auto -l" (expand-alias {"ls" "ls --color=auto"} "ls -l")))
  (is (= "lshw" (expand-alias {"ls" "ls --color=auto"} "lshw")))

  (is (= "my alias expansion" (do (defalias myalias "my alias expansion")
                                  (expand-alias "myalias"))))
  (is (= "my str alias" (do (defalias "myalias2" "my str alias")
                            (expand-alias "myalias2")))))

(deftest abbreviations
  (is (= "ls --color=auto" (expand-abbreviation {"ls" "ls --color=auto"} "ls")))
  (is (= " ls --color=auto" (expand-abbreviation {"ls" "ls --color=auto"} " ls")))
  (is (= "ls -l" (expand-abbreviation {"ls" "ls --color=auto"} "ls -l")))
  (is (= "lshw" (expand-abbreviation {"ls" "ls --color=auto"} "lshw")))

  (is (= "my abbr expansion" (do (defabbr myabbr "my abbr expansion")
                                 (expand-abbreviation "myabbr"))))
  (is (= "my str abbr" (do (defabbr "myabbr2" "my str abbr")
                           (expand-abbreviation "myabbr2")))))

(deftest commands
  (is (= "abcX" (do (closh (pr-str '(defcmd cmd-x [s] (str s "X"))))
                    (:stdout (closh "cmd-x abc")))))

  (is (= "abcY" (do (closh (pr-str '(defcmd cmd-y (fn [s] (str s "Y")))))
                    (:stdout (closh "cmd-y abc")))))

  (is (= "abcZ" (do (closh (pr-str '(do (defn fn-z [s] (str s "Z"))
                                        (defcmd cmd-z fn-z))))
                    (:stdout (closh "cmd-z abc"))))))
