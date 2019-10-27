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
   [closh.zero.utils.sci :refer [ctx]]
   [sci.core :as sci]
   [sci.impl.interpreter :as interpreter]
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
    (print result)
    (flush)))

(defn -main [& args]
  (reset! process/*cwd* (System/getProperty "user.dir"))
  (let [cmd (or (first args) "echo hello clojure")
        expr (format "(let [parsed (closh.zero.parser/parse '[%s])]
                        (-> parsed
                            (closh.zero.compiler/compile-interactive)
                            (closh.zero.pipeline/wait-for-pipeline)))"
                     cmd)
        opts (update ctx :bindings merge {'prn prn
                                          'closh.zero.compiler/compile-interactive
                                          closh.zero.compiler/compile-interactive
                                          'closh.zero.parser/parse closh.zero.parser/parse})
        ctx (interpreter/opts->ctx opts)
        expr (sci/eval-string expr ctx)
        expr (interpreter/eval-edn-vals ctx [expr])]
    (repl-print expr)))
