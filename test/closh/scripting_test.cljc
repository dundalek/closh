(ns closh.scripting-test
  (:require [clojure.test :refer [deftest testing is are]]
            [closh.zero.core :refer [shx]]
            [closh.zero.pipeline :refer [process-output process-value pipe]]))

(defn closh [& args]
  (shx "clojure" (concat ["-m" "closh.zero.frontend.rebel"] args)))

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

    "true"
    (closh "-e" "(print (:dynamic (meta ^:dynamic {})))")))

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

    "5:1"
    #"/throw2\.cljc:(\d+:\d+)"
    (closh "fixtures/script-mode-tests/throw2.cljc")

    "3"
    #"Execution error at .* \(REPL:(\d+)\)"
    (closh "-e" "\n\n(throw (Exception. \"my exception message\"))")))
