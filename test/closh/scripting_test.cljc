(ns closh.scripting-test
  (:require [clojure.test :refer [deftest are]]
            [closh.zero.core :refer [shx]]
            [closh.zero.pipeline :refer [process-output process-value pipe]]))

(def sci? #?(:clj  (System/getenv "__CLOSH_USE_SCI_EVAL__")
             :cljs false))

(def sci-complete? #?(:clj  (System/getenv "__CLOSH_USE_SCI_COMPLETE__")
                      :cljs false))

(defn closh [& args]
  (shx "clojure" (concat (if sci?
                           ["-M:sci" "-m" "closh.zero.frontend.sci"]
                           ["-M" "-m" "closh.zero.frontend.rebel"])
                         args)))

(deftest scripting-test

  (are [x y]
       (= x (process-output y))

    "a b\n"
    (closh "-e" "echo a b")

    "a b\n"
    (pipe (shx "echo" ["echo a b"])
          (closh "-"))

    "3\n"
    (pipe (shx "echo" ["echo (+ 1 2)"])
          (closh "-"))

    "bar\n"
    (closh "fixtures/script-mode-tests/bar.cljc")

    "foo\nbar\n"
    (closh "fixtures/script-mode-tests/foo.cljc")

    "Hi World\n"
    (closh "-i" "fixtures/script-mode-tests/cmd.cljc" "-e" "my-hello World")

    "Hello World\n"
    (closh "-i" "fixtures/script-mode-tests/cmd2.cljc")

    "(\"a\" \"b\")\n"
    (closh "fixtures/script-mode-tests/args.cljc" "a" "b")

    "a b\n"
    (closh "fixtures/script-mode-tests/cond.cljc")

    ;; TODO metadata reader for sci
    "true"
    (closh "-e" (if sci?
                  "(print (:dynamic (meta (with-meta {} {:dynamic true}))))"
                  "(print (:dynamic (meta ^:dynamic {})))"))))

(when (or (not sci?)
          sci-complete?)
  (deftest scripting-errors-test

    (are [result regex cmd]
         (= result (->> (:stderr (process-value cmd))
                        (re-find regex)
                        (second)))

      "5"
      #"throw1\.cljc:(\d+)"
      (closh "fixtures/script-mode-tests/throw1.cljc")

      "4"
      #"Execution error at user/eval\d+ \(REPL:(\d+)\)"
      (pipe "\n\n\n (throw (Exception. \"my exception message\"))" (closh "-"))

      ; TODO
      ; "2:4"
      ; (if (System/getenv "__CLOSH_USE_SCI_EVAL__")
      ;   #"Syntax error reading source at \(REPL:(\d+:\d+)\)"
      ;   #"Syntax error \(ExceptionInfo\) compiling at \(REPL:(\d+:\d+)\)")
      ; (pipe "\n  )" (closh "-"))

      "2"
      #"Execution error at user/eval\d+\$my-throw \(throw2\.cljc:(\d+)\)"
      (closh "fixtures/script-mode-tests/throw2.cljc")

      "3"
      #"Execution error at .* \(REPL:(\d+)\)"
      (closh "-e" "\n\n(throw (Exception. \"my exception message\"))")

      ; "2"
      ; #"Execution error at .* \(REPL:(\d+)\)"
      ; (closh "-e" "\n  )")
      )))
