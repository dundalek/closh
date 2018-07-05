(ns closh.process-test
  (:require [clojure.test :refer [deftest is are]]
            #?(:cljs [closh.zero.pipeline :refer [process-output]])
            #?(:cljs [closh.core
                      :refer [shx]
                      :refer-macros [sh sh-str defalias defabbr]])
            #?(:clj [clojure.java.io :as io])
            #?(:clj [me.raynes.conch.low-level :as sh])))

#?(:clj
   (defn builder-redirect
     ([builder fd]
      (case fd
        0 (.redirectInput builder)
        1 (.redirectOutput builder)
        2 (.redirectError builder)
        (throw (Exception. (str "Unsupported file descriptor: " fd " (only file descriptors 0, 1, 2 are supported)")))))
     ([builder fd target]
      (case fd
        0 (.redirectInput builder target)
        1 (.redirectOutput builder target)
        2 (.redirectError builder target)
        (throw (Exception. (str "Unsupported file descriptor: " fd " (only file descriptors 0, 1, 2 are supported)")))))))

#?(:clj
   (defn shx
        "Executes a command as child process."
        ([cmd] (shx cmd []))
        ([cmd args] (shx cmd args {}))
        ([cmd args opts]
         (let [builder (ProcessBuilder. (into-array String (map str (concat [cmd] args))))]
           (when (:redir opts)
             (doseq [[op fd target] (:redir opts)]
               (case op
                 :rw (throw (Exception. "Read/Write redirection is not supported"))
                 (let [redirect (case op
                                  :in (java.lang.ProcessBuilder$Redirect/from (io/file target))
                                  :out (java.lang.ProcessBuilder$Redirect/to (io/file target))
                                  :append (java.lang.ProcessBuilder$Redirect/appendTo (io/file target))
                                  :set (builder-redirect builder target))]
                   (builder-redirect builder fd redirect)))))
           (let [process (.start builder)]
             {:out (.getInputStream process)
              :in  (.getOutputStream process)
              :err (.getErrorStream process)
              :process process})))))

#?(:clj
   (defn process-output
        "Returns for a process to finish and returns output to be printed out."
        [proc]
        (sh/stream-to-string proc :out)))

(deftest run-test

  (is (= "5\n" (process-output (shx "echo" [(+ 2 3)]))))

  (is (= "" (-> (shx "ls" [] {:redir [[:out 1 "/dev/null"]]})
                process-output)))

  (is (= "abc\n" (process-output (shx "echo" ["abc"]))))

  (is (= "a\nb\n" (do
                   (process-output (shx "echo" ["a"] {:redir [[:out 1 "file.txt"]]}))
                   (process-output (shx "echo" ["b"] {:redir [[:append 1 "file.txt"]]}))
                   (process-output (shx "cat" ["file.txt"])))))

  (is (= "x\n" (do
                 (process-output (shx "echo" ["x"] {:redir [[:out 1 "file.txt"]]}))
                 (process-output (shx "cat" ["file.txt"]))))))
