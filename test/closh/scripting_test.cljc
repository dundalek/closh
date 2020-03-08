(ns closh.scripting-test
  (:require [clojure.test :refer [deftest are]]
            [closh.zero.core :refer [shx]]
            [closh.zero.pipeline :refer [process-output process-value pipe]]))

(defn closh [& args]
  (shx "clojure" (concat (if (System/getenv "__CLOSH_USE_SCI_EVAL__")
                           ["-A:sci" "-m" "closh.zero.frontend.sci"]
                           ["-m" "closh.zero.frontend.rebel"])
                         args)))

(deftest scripting-test

  (are [x y] (= x (process-output y))

    "a b\n"
    (closh "-e" "echo a b")

    "a b\n"
    (pipe (shx "echo" ["echo a b"])
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
    (closh "-e" (if (System/getenv "__CLOSH_USE_SCI_EVAL__")
                  "(print (:dynamic (meta (with-meta {} {:dynamic true}))))"
                  "(print (:dynamic (meta ^:dynamic {})))"))))

(deftest scripting-errors-test

  (are [result regex cmd] (= result (->> (:stderr (process-value cmd))
                                         (re-find regex)
                                         (second)))

    "5:3"
    #"/throw1\.cljc:(\d+:\d+)"
    (closh "fixtures/script-mode-tests/throw1.cljc")

    "4:2"
    #"Syntax error compiling at \(REPL:(\d+:\d+)\)"
    (pipe "\n\n\n (throw (Exception. \"my exception message\"))" (closh "-"))

    ; TODO
    ; "2:4"
    ; (if (System/getenv "__CLOSH_USE_SCI_EVAL__")
    ;   #"Syntax error reading source at \(REPL:(\d+:\d+)\)"
    ;   #"Syntax error \(ExceptionInfo\) compiling at \(REPL:(\d+:\d+)\)")
    ; (pipe "\n  )" (closh "-"))

    "5:1"
    #"/throw2\.cljc:(\d+:\d+)"
    (closh "fixtures/script-mode-tests/throw2.cljc")

    (if (System/getenv "__CLOSH_USE_SCI_EVAL__")
      "Execution error at"
      "3")
    (if (System/getenv "__CLOSH_USE_SCI_EVAL__")
      ;; TODO handle location for sci in ex-triage :execution phase
      #"(Execution error at)"
      #"Execution error at .* \(REPL:(\d+)\)")
    (closh "-e" "\n\n(throw (Exception. \"my exception message\"))")))

    ; "2"
    ; #"Execution error at .* \(REPL:(\d+)\)"
    ; (closh "-e" "\n  )")))

