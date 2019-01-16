(ns closh.scripting-test
  (:require [clojure.test :refer [deftest testing is are]]
            [closh.zero.core :refer [shx]]
            [closh.zero.pipeline :refer [process-output pipe]]))

(defn closh [& args]
  (shx "clojure" (concat ["-m" "closh.zero.frontend.rebel"] args)))

(deftest scripting-test

  (is (= "a b\n" (process-output (closh "-e" "echo a b"))))

  (is (= "a b\n" (process-output (pipe (shx "echo" ["echo a b"])
                                       (closh "-")))))

  (is (= "bar\n" (process-output (closh "resources/fixtures/script-mode-tests/bar.cljc"))))

  (is (= "foo\nbar\n" (process-output (closh "resources/fixtures/script-mode-tests/foo.cljc"))))

  (is (= "Hi World\n" (process-output (closh "-i" "resources/fixtures/script-mode-tests/cmd.cljc" "-e" "my-hello World")))))
