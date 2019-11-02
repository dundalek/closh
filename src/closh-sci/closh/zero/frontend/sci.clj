(ns closh.zero.frontend.sci
  (:gen-class)
  (:require
   #_[closh.zero.reader :as reader]
   #_[clojure.tools.reader.reader-types :refer [string-push-back-reader]]
   #_[clojure.edn :as edn]
   [edamame.core :as edamame]
   [closh.zero.compiler]
   [closh.zero.parser :as parser]
   [closh.zero.pipeline]
   [closh.zero.platform.eval :as eval]
   [closh.zero.platform.process :as process]
   [closh.zero.pipeline]
   [closh.zero.env :as env])
  (:import (java.io PushbackReader StringReader)))

#_(require '[clojure.pprint :refer [pprint]])

#_(defn read-all [rdr]
    (let [eof (Object.)
          opts {:eof eof :read-cond :allow :features #{:clj}}]
      (loop [forms []]
        (let [form (edn/read opts rdr)] ;; NOTE: clojure.core/read triggers the locking issue
          (if (= form eof)
            (seq forms)
            (recur (conj forms form)))))))

(defn repl-print
  [result]
  (when-not (or (nil? result)
                (identical? result env/success)
                (process/process? result))
    (if (string? result)
      (print result)
      (pr result))
    (flush)))

(defn -main [& args]
  (let [cmd (or (first args) "echo hello clojure")]
    (reset! process/*cwd* (System/getProperty "user.dir"))
    ;; works:
    #_(println (read-all (PushbackReader. (StringReader. cmd))))
    ;; works:
    #_(println (parser/parse (read-all (PushbackReader. (StringReader. cmd)))))
    ;; works:
    #_(clojure.core/->
       (closh.zero.core/shx (closh.zero.core/expand-command "echo")
                            [(closh.zero.core/expand "hello")
                             (closh.zero.core/expand "clojure")]
                            {:redir [[:set 0 :stdin] [:set 2 :stderr] [:set 1 :stdout]]}))
    ;; works:
    #_(println
       `(-> ~(closh.zero.compiler/compile-interactive
              (closh.zero.parser/parse (read-all (PushbackReader. (StringReader. cmd)))))
            (closh.zero.pipeline/wait-for-pipeline)))
    ;; also works:
    (repl-print
      (eval/eval
        `(-> ~(closh.zero.compiler/compile-interactive
               (closh.zero.parser/parse
                (edamame/parse-string-all cmd)
                #_(read-all (PushbackReader. (StringReader. cmd)))))
             (closh.zero.pipeline/wait-for-pipeline))))))
