(ns closh.process-test
  (:require [clojure.test :refer [deftest is are]]
            [closh.zero.platform.process :as process :refer [shx process?]]
            [closh.zero.pipeline :refer [process-output]]))

#?(:cljs
   (do
     (def tmp (js/require "tmp"))
     (tmp.setGracefulCleanup)))

(defn get-tmpfile []
  #?(:cljs
     (.-name (tmp.fileSync))
     :clj
     (let [file (java.io.File/createTempFile "closh-test-" ".txt")]
       (.deleteOnExit file)
       (.getAbsolutePath file))))

(deftest run-test

  (is (= "5\n" (process-output (shx "echo" [(+ 2 3)]))))

  (is (= "" (-> (shx "ls" [] {:redir [[:out 1 "/dev/null"]]})
                process-output)))

  (is (= "abc\n" (process-output (shx "echo" ["abc"]))))

  (is (= "a\nb\n" (let [f (get-tmpfile)]
                   (process-output (shx "echo" ["a"] {:redir [[:out 1 f]]}))
                   (process-output (shx "echo" ["b"] {:redir [[:append 1 f]]}))
                   (process-output (shx "cat" [f])))))

  (is (= "x\n" (let [f (get-tmpfile)]
                 (process-output (shx "echo" ["x"] {:redir [[:out 1 f]]}))
                 (process-output (shx "cat" [f])))))

  (is (= true (process? (shx "ls"))))

  (is (= 0 (-> (shx "echo")
               (process/wait)
               (process/exit-code))))

  (is (= 1 (-> (shx "bash" ["-c" "exit 1"])
               (process/wait)
               (process/exit-code)))))
