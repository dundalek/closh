(ns closh.zero.frontend.plain
  (:gen-class)
  (:require ;; [closh.zero.platform.eval :as eval]
            ;; [closh.zero.compiler]
            ;; [closh.zero.parser]
            ;; [closh.zero.pipeline]
            [clojure.edn :as edn]
            #_[closh.zero.reader :as reader]
            #_[clojure.tools.reader.reader-types :refer [string-push-back-reader]])
  (:import (java.io PushbackReader StringReader)))

(defn read-all [rdr]
 (let [eof (Object.)
       opts {:eof eof :read-cond :allow :features #{:clj}}]
   (loop [forms []]
      (let [form (edn/read opts rdr)] ;; NOTE: clojure.core/read triggers the locking issue
        (if (= form eof)
          (seq forms)
          (recur (conj forms form)))))))

(defn -main [& args]
  (let [cmd (or (first args) "echo hello clojure")]
    (println (read-all (PushbackReader. (StringReader. cmd))))
    #_(eval/eval
     `(-> ~(closh.zero.compiler/compile-interactive
            (closh.zero.parser/parse (read-all (PushbackReader. (StringReader. cmd)))))
           (closh.zero.pipeline/wait-for-pipeline)))))
