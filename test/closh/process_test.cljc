(ns closh.process-test
  (:require [clojure.test :refer [deftest is are]]
            [closh.test-util.util :refer [null-file]]
            [closh.zero.platform.process :as process :refer [shx process? cwd chdir]]
            [closh.zero.pipeline :refer [process-output]]
            #?(:cljs [path]))
  #?(:clj (:import [java.io File])))

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

  (is (= "" (-> (shx "ls" [] {:redir [[:out 1 null-file]]})
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
               (process/exit-code))))

  (is (= (cwd)
         (do
           (chdir "src")
           (chdir "..")
           (cwd)))
      "When cd back to parent directory the path should be canonical and not contain ..")

  (is (= (cwd)
         (let [d #?(:clj (.getName (File. (.getCanonicalPath (File. (cwd)))))
                    :cljs (path.basename (path.normalize (cwd))))]
           (do
             (chdir "..")
             (chdir d)
             (cwd))))))
