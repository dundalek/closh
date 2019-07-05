(ns closh.core-test
  (:require [clojure.test :refer [deftest testing is are]]
            [closh.test-util.util :refer [null-file]]
            [clojure.spec.alpha :as s]
            [clojure.string]
            [closh.zero.reader]
            [closh.zero.builtin :refer [getenv setenv]]
            [closh.zero.env]
            [closh.zero.platform.io]
            [closh.zero.platform.process :as process]
            #?(:cljs [closh.zero.platform.eval :refer [execute-command-text]])
            #?(:cljs [closh.zero.platform.util :refer [wait-for-event]])
            [clojure.tools.reader.reader-types :refer [string-push-back-reader]]
            [closh.zero.platform.io]
            [closh.zero.pipeline :as pipeline :refer [process-output process-value wait-for-pipeline pipe pipe-multi pipe-map pipe-filter pipeline-value pipeline-condition]]
            [closh.zero.core :refer [shx expand expand-partial expand-alias expand-abbreviation]]
            [closh.zero.macros #?(:clj :refer :cljs :refer-macros) [sh sh-str defalias defabbr]]
            #?(:cljs [lumo.io :refer [spit slurp]])))

#?(:clj
   (do
     (def user-namespace (create-ns 'user))
     (binding [*ns* user-namespace]
       (eval closh.zero.env/*closh-environment-requires*)))
   :cljs
   (do
     (def fs (js/require "fs"))
     (def tmp (js/require "tmp"))

     ;; Clean up tmp files on unhandled exception
     (tmp.setGracefulCleanup)

     (closh.zero.platform.eval/execute-text
       (str (pr-str closh.zero.env/*closh-environment-requires*)))))


(defn with-tempfile [cb]
 #?(:cljs
    (let [file (tmp.fileSync)
             f (.-name file)
             result (cb f)
             out (fs.readFileSync f "utf-8")]
         (.removeCallback file)
         [out result])
    :clj
    (let [file (java.io.File/createTempFile "closh-test-" ".txt")
          f (.getAbsolutePath file)
          _ (.deleteOnExit file)
          result (cb f)]
      [(slurp f) result])))

(defn bash [cmd]
  (pipeline/process-value (shx "bash" ["-c" cmd])))

(defn create-fake-writer []
  #?(:clj (java.io.ByteArrayOutputStream.)
     :cljs
      (let [file (tmp.fileSync)
            name (.-name file)
            stream (.createWriteStream fs name)]
        (wait-for-event stream "open")
        {:file file
         :name name
         :stream stream})))

(defn get-fake-writer [writer]
  #?(:clj (java.io.PrintStream. writer)
     :cljs (:stream writer)))

(defn str-fake-writer [writer]
  #?(:clj (str writer)
     :cljs (let [content (.readFileSync fs (:name writer) "utf-8")]
             (.removeCallback (:file writer))
             content)))

(defn closh-spawn-helper [cmd]
  #?(:cljs (pipeline/process-value (shx "lumo" ["-K" "-c" "src/common:src/lumo:test" "-m" "closh.test-util.spawn-helper" cmd]))
     :clj (pipeline/process-value (shx "clojure" ["-A:test" "-m" "closh.test-util.spawn-helper" cmd]))))

(defn closh-spawn [cmd]
  (let [out (create-fake-writer)
        err (create-fake-writer)]
    (binding [closh.zero.platform.io/*stdout* (get-fake-writer out)
              closh.zero.platform.io/*stderr* (get-fake-writer err)]
      (let [code (closh.zero.reader/read (string-push-back-reader cmd))
            proc #?(:clj (eval `(-> ~(closh.zero.compiler/compile-batch (closh.zero.parser/parse code))
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
             :code code}))))))

(defn closh [cmd]
  #?(:cljs (execute-command-text cmd closh.zero.reader/read-sh-value)
     :clj (let [code (closh.zero.compiler/compile-batch
                       (closh.zero.parser/parse (closh.zero.reader/read (string-push-back-reader cmd))))]
            (binding [*ns* user-namespace]
              (closh.zero.pipeline/process-value (eval code))))))

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
          #?(:cljs (.createReadStream (js/require "fs") "package.json")
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
    "cd . && echo ok")

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

  (are [x y] (= x (first (with-tempfile (fn [f] (closh y)))))

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

  (are [x y] (= x (second (with-tempfile (fn [f] (closh y)))))

    {:stdout "", :stderr "", :code 0}
    (str "echo hello | (clojure.string/upper-case) > " f))

  (are [x y] (= (first (with-tempfile (fn [f] (bash x))))
                (first (with-tempfile (fn [f] (closh y)))))

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

    "mkdir x/y/z || echo FAILED"
    "mkdir x/y/z || echo FAILED"

    "for f in test/closh/*.cljc; do echo $f; cat $f; done"
    "ls test/closh/*.cljc |> (map #(do (sh echo (str %)) (sh cat (str %))))"))

(deftest test-builtin-getenv-setenv

  (is (= (pr-str (setenv "ONE" "6")) (:stdout (closh "setenv \"ONE\" \"6\""))))
  (is (= "42" (:stdout (closh "(sh setenv ONE 42) (sh getenv ONE)"))))
  (is (= "42" (:stdout (closh "(sh setenv \"ONE\" \"42\") (sh getenv \"ONE\")"))))
  (is (= (getenv "ONE") (:stdout (closh "getenv \"ONE\"")))))

(deftest commands
  (is (= "abcX" (do (closh (pr-str '(defcmd cmd-x [s] (str s "X"))))
                    (:stdout (closh "cmd-x abc")))))

  (is (= "abcX" (do (closh (pr-str '(defcmd cmd-x [s] (str s "X"))))
                    (:stdout (closh "(cmd-x \"abc\")")))))

  (is (= "abcY" (do (closh (pr-str '(defcmd cmd-y (fn [s] (str s "Y")))))
                    (:stdout (closh "cmd-y abc")))))

  (is (= "original fn" (do (closh (pr-str '(do (defn cmd-y [_] "original fn")
                                               (defcmd cmd-y (fn [s] (str s "Y"))))))
                           (:stdout (closh "(cmd-y \"abc\")")))))

  (is (= "abcZ" (do (closh (pr-str '(do (defn fn-z [s] (str s "Z"))
                                        (defcmd cmd-z fn-z))))
                    (:stdout (closh "cmd-z abc")))))

  (is (= "ABC" (do (closh (pr-str '(defcmd cmd-upper clojure.string/upper-case)))
                   (:stdout (closh "echo -n abc | cmd-upper")))))

  (is (= "ABC" (do (closh (pr-str '(defcmd cmd-upper clojure.string/upper-case)))
                   (:stdout (closh "echo -n abc | cmd-upper | cat")))))

  (is (= "ABC" (do (closh (pr-str '(defcmd cmd-upper clojure.string/upper-case)))
                   (:stdout (closh "(str \"abc\") | cmd-upper")))))

  (is (= "hi" (do (closh (pr-str '(defcmd cmd-hello [] "hi")))
                  (:stdout (closh "cmd-hello")))))

  (is (= "HI" (do (closh (pr-str '(defcmd cmd-hello [] "hi")))
                  (:stdout (closh "cmd-hello | (clojure.string/upper-case)")))))

  (is (= "HI" (do (closh (pr-str '(defcmd cmd-hello [] "hi")))
                  (:stdout (closh "cmd-hello | tr \"[:lower:]\" \"[:upper:]\"")))))

  (is (= "ABC" (do (closh (pr-str '(defcmd cmd-upper clojure.string/upper-case)))
                   (first (with-tempfile (fn [f] (closh (str "echo -n abc | cmd-upper > " f)))))))))
