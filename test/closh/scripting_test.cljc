(ns closh.scripting-test
  (:require [clojure.test :refer [deftest testing is are]]
            [closh.zero.core :refer [shx]]
            [closh.zero.pipeline :refer [process-output process-value pipe]]))

(defn closh [& args]
  (shx "clojure" (concat ["-m" "closh.zero.frontend.rebel"] args)))

(deftest scripting-test

  (is (= "a b\n" (process-output (closh "-e" "echo a b"))))

  (is (= "a b\n" (process-output (pipe (shx "echo" ["echo a b"])
                                       (closh "-")))))

  (is (= "bar\n" (process-output (closh "resources/fixtures/script-mode-tests/bar.cljc"))))

  (is (= "foo\nbar\n" (process-output (closh "resources/fixtures/script-mode-tests/foo.cljc"))))

  (is (= "Hi World\n" (process-output (closh "-i" "resources/fixtures/script-mode-tests/cmd.cljc" "-e" "my-hello World"))))

  (is (= "(\"a\" \"b\")\n" (process-output (closh "resources/fixtures/script-mode-tests/args.cljc" "a" "b"))))

  (is (= "a b\n" (process-output (closh "resources/fixtures/script-mode-tests/cond.cljc")))))


(deftest scripting-errors-test

  (are [result regex cmd] (= result (->> (:stderr (process-value cmd))
                                         (re-find regex)
                                         (second)))

    "5:3"
    #"/throw1\.cljc:(\d+:\d+)"
    (closh "resources/fixtures/script-mode-tests/throw1.cljc")

    "4:2"
    #"Syntax error compiling at \((\d+:\d+)\)"
    (pipe "\n\n\n (throw (Exception. \"my exception message\"))" (closh "-"))

    "5:1"
    #"/throw2\.cljc:(\d+:\d+)"
    (closh "resources/fixtures/script-mode-tests/throw2.cljc")

    "3"
    #"\(NO_SOURCE_FILE:(\d+)\)"
    (closh "-e" "\n\n(throw (Exception. \"my exception message\"))")))
