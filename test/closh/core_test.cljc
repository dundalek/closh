(ns closh.core-test
  (:require [clojure.test :refer [deftest testing is are]]
            [closh.test-util.util :refer [null-file with-tempfile with-tempfile-content create-fake-writer get-fake-writer str-fake-writer]]
            [clojure.string :as str]
            [closh.zero.reader :as reader]
            [closh.zero.builtin :refer [getenv setenv]]
            [closh.zero.env]
            [closh.zero.platform.io]
            [closh.zero.platform.process :as process]
            #?(:cljs [closh.zero.platform.eval :refer [execute-command-text]])
            [closh.zero.platform.io]
            [closh.zero.pipeline :as pipeline :refer [process-output process-value wait-for-pipeline pipe pipe-multi pipe-map pipe-filter pipeline-value pipeline-condition]]
            [closh.zero.core :refer [shx expand expand-partial expand-alias expand-abbreviation]]
            [closh.zero.macros #?(:clj :refer :cljs :refer-macros) [sh sh-str defalias defabbr]]
            #?(:cljs [lumo.io :refer [spit slurp]])
            #?(:cljs [fs])
            #?(:clj [closh.zero.platform.eval :as eval])))

(def sci? (process/getenv "__CLOSH_USE_SCI_NATIVE__"))

#?(:clj
   (do
     (def user-namespace (create-ns 'user))
     (binding [*ns* user-namespace]
       (eval/eval-closh-requires)))
   :cljs
   (closh.zero.platform.eval/execute-text
     (str (pr-str closh.zero.env/*closh-environment-requires*))))

(defn bash [cmd]
  (pipeline/process-value (shx "bash" ["-c" cmd])))

(defn closh-spawn-helper [cmd]
  #?(:cljs (pipeline/process-value (shx "lumo" ["-K" "-c" "src/common:src/lumo:test" "-m" "closh.test-util.spawn-helper" cmd]))
     :clj (pipeline/process-value (shx "clojure" [(if (System/getenv "__CLOSH_USE_SCI_EVAL__") "-A:test:sci" "-A:test") "-m" "closh.test-util.spawn-helper" cmd]))))

(if sci?
  (defn closh-spawn [cmd]
    (let [result
          (pipeline/process-value (shx "./closh-zero-sci" [cmd]))
          #_(pipeline/process-value (shx "java" ["-jar" "target/closh-zero-sci.jar" cmd]))
          #_(pipeline/process-value (shx "clojure" ["-A:sci" "-m" "closh.zero.frontend.sci" cmd]))]
      (when-not (str/blank? (:stderr result))
        (println "STDERR:")
        (println (:stderr result)))
      result))
  (defn closh-spawn [cmd]
    (let [out (create-fake-writer)
          err (create-fake-writer)]
      (binding [closh.zero.platform.io/*stdout* (get-fake-writer out)
                closh.zero.platform.io/*stderr* (get-fake-writer err)]
        (let [code (reader/read (reader/string-reader cmd))
              proc #?(:clj (eval/eval `(-> ~(closh.zero.compiler/compile-batch (closh.zero.parser/parse code))
                                          (closh.zero.pipeline/wait-for-pipeline)))
                      :cljs (execute-command-text (pr-str (conj code 'closh.zero.macros/sh))))]
          (if (process/process? proc)
            (do
              (process/wait proc)
              {:stdout (str-fake-writer out)
               :stderr (str-fake-writer err)
               :code (process/exit-code proc)})
            (let [{:keys [stdout stderr code]} (process-value proc)]
              {:stdout (str (str-fake-writer out) stdout)
               :stderr (str (str-fake-writer err) stderr)
               :code code})))))))

(if sci?
  (def closh closh-spawn)
  (defn closh [cmd]
    #?(:cljs (execute-command-text cmd closh.zero.reader/read-sh-value)
       :clj (let [code (closh.zero.compiler/compile-batch
                         (closh.zero.parser/parse (reader/read (reader/string-reader cmd))))]
              (binding [*ns* user-namespace]
                (closh.zero.pipeline/process-value (eval/eval code)))))))

(deftest run-test

  (is (= "package.json\n" (process-output (shx "ls" [(expand "package.js*")]))))

  (is (= (process-output (shx "ls"))
         (process-output (shx "ls" ["-d" (expand "*")]))))

  (is (= (process-output (shx "ls" ["scripts"]))
         (do (process/chdir "scripts")
           (let [out (process-output (shx "ls" ["-d" (expand "*")]))]
             (process/chdir "..")
             out))))

  (is (= (-> (slurp "package.json")
             (clojure.string/trimr)
             (clojure.string/split-lines)
             (seq))
         (closh.zero.platform.io/line-seq
          #?(:cljs (fs/createReadStream "package.json")
             :clj (java.io.FileInputStream. "package.json")))))

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
    "echo hi | (str/upper-case)"

    "3\n"
    "echo (+ 1 2)"

    "x\n"
    "echo (sh-str echo x)"

    "2"
    "(sh-str echo \"a\n\b\" |> (count))"

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
    "echo abc '> /tmp/x"

    "{"
    "cat < package.json | (first)"

    "ok\n"
    "cd . && echo ok"

    "ok\n"
    "fixtures/echo-tester ok"

    "ok\n"
    "./fixtures/echo-tester ok"

    ;; Make sure updating PATH is reflected for executable lookup
    "ok\n"
    "setenv PATH (str (getenv \"PWD\") \"/fixtures:\" (getenv \"PATH\")) && echo-tester ok")

    ; TODO: Fix input redirection to a function
    ; "{"
    ; "(first) < package.json")

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

    "cat < package.json | cat"

    "/bin/ech? x")

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
    "(-> (/ (+ 1 (Math/sqrt 5)) 2) str (subs 0 10))"

    (str "cat < package.json 2>" null-file " | cat")
    (str "cat < package.json 2 > " null-file " | cat")

    "for f in test/closh/*.cljc; do echo $f; cat $f; done"
    "ls test/closh/*.cljc |> (map #(str % \"\\n\" (sh-str cat (str %)))) | cat"

    "if test -f package.json; then echo file exists; else echo no file; fi"
    "echo (if (sh-ok test -f package.json) \"file exists\" \"no file\")"

    "if test -f asdfgh.json; then echo file exists; else echo no file; fi"
    "echo (if (sh-ok test -f asdfgh.json) \"file exists\" \"no file\")"

    "ls -l `echo *.json *.md`"
    "ls -l (sh-seq echo *.json *.md)"

    "bash -c \"echo err 1>&2; echo out\""
    "bash -c \"echo err 1>&2; echo out\"")

  (are [x y] (= x (with-tempfile-content (fn [f] (closh y))))

    "x1\n"
    (str "echo x1 > " f)

    ; TODO: enable spit - there is a problem with test runner when lumo.io is required
    ; "x2\n"
    ; (str "echo x2 | (spit \"" f "\")")

    "x3\ny1\n"
    (str "(sh echo x3 > \"" f "\") (sh echo y1 >> \"" f "\")")

    ""
    (str "echo x4 2 > " f)

    "HELLO\n"
    (str "echo hello | (clojure.string/upper-case) > " f)

    "H"
    (str "echo hello | (first) | (clojure.string/upper-case) > " f)

    "X3\nY1\n"
    (str "(sh echo x3 | (clojure.string/upper-case) > \"" f "\")"
         "(sh echo y1 | (clojure.string/upper-case) >> \"" f "\")"))

  (are [x y] (= x (with-tempfile (fn [f] (closh y))))

    {:stdout "", :stderr "", :code 0}
    (str "echo hello | (clojure.string/upper-case) > " f))

  (are [x y] (= (with-tempfile-content (fn [f] (bash x)))
                (with-tempfile-content (fn [f] (closh y))))

    ; macOS does not have `tac` command but `tail -r` can be used instead
    (str "(ls | tac || ls | tail -r) > " f)
    (str "ls |> (reverse) > " f)))

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

  (is (= "2\n1\ngo\n"
         (-> '(do (sh bash -c "sleep 0.2 && echo 2")
                  (sh bash -c "sleep 0.1 && echo 1")
                  (sh bash -c "echo go"))
             pr-str closh-spawn :stdout)))

  (is (= "2\n1\ngo\n"
         (-> '(sh bash -c "sleep 0.2 && echo 2" \;
                  bash -c "sleep 0.1 && echo 1" \;
                  bash -c "echo go")
             pr-str closh-spawn :stdout)))

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
             (select-keys [:stdout :stderr])))))

(deftest run-extra-special-cases
  (are [x y] (= (bash x) (closh-spawn-helper y))

    #?(:cljs "mkdir x/y/z || echo FAILED"
        ;; When using lookup executable as workaround for ProcessBuilder PATH handling, the Clojure version reports full absolute path when printing error
       :clj "/bin/mkdir x/y/z || echo FAILED")
    "mkdir x/y/z || echo FAILED"

    "for f in test/closh/*.cljc; do echo $f; cat $f; done"
    "ls test/closh/*.cljc |> (map #(do (sh echo (str %)) (sh cat (str %))))"))

(deftest test-builtin-getenv-setenv

  (is (= (pr-str (setenv "ONE" "6")) (:stdout (closh "setenv \"ONE\" \"6\""))))
  (is (= "42" (:stdout (closh "(sh setenv ONE 42) (sh getenv ONE)"))))
  (is (= "42" (:stdout (closh "(sh setenv \"ONE\" \"42\") (sh getenv \"ONE\")"))))
  (is (= (getenv "ONE") (:stdout (closh "getenv \"ONE\"")))))

(deftest test-builtin-cd

  (is (str/ends-with? (let [result (str/trim (:stdout (closh "mkdir -p \"out/1\" && cd out && cd 1 && pwd")))]
                        (closh "cd ../..")
                        result)
                      "/out/1")))

(deftest commands
  (are [x y] (= x (:stdout (closh (pr-str y))))

    "abcX" '(do (defcmd cmd-x [s] (str s "X"))
                (sh cmd-x "abc"))

    "abcX" '(do (defcmd cmd-x [s] (str s "X"))
                (cmd-x "abc"))

    "abcY" '(do (defcmd cmd-y (fn [s] (str s "Y")))
                (sh cmd-y abc))

    "original fn" '(do (defn cmd-y [_] "original fn")
                       (defcmd cmd-y (fn [s] (str s "Y")))
                       (cmd-y "abc"))

    "abcZ" '(do (defn fn-z [s] (str s "Z"))
                (defcmd cmd-z fn-z)
                (sh cmd-z abc))

    "ABC" '(do (defcmd cmd-upper clojure.string/upper-case)
               (sh echo -n abc | cmd-upper))

    "ABC" '(do (defcmd cmd-upper clojure.string/upper-case)
               (sh-str echo -n abc | cmd-upper | cat))

    "ABC" '(do (defcmd cmd-upper clojure.string/upper-case)
               (sh (str "abc") | cmd-upper))

    "hi" '(do (defcmd cmd-hello [] "hi")
              (sh cmd-hello))

    "HI" '(do (defcmd cmd-hello [] "hi")
              (sh cmd-hello | (clojure.string/upper-case)))

    "HI" '(do (defcmd cmd-hello [] "hi")
              (sh-str cmd-hello | tr "[:lower:]" "[:upper:]")))

  (is (= "ABC" (do (closh (pr-str '(defcmd cmd-upper clojure.string/upper-case)))
                   (with-tempfile-content (fn [f] (closh (str "echo -n abc | cmd-upper > " f))))))))
