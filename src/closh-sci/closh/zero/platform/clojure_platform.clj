(ns closh.zero.platform.clojure-platform
  (:refer-clojure :exclude [eval read load-reader read+string])
  (:require [closh.zero.reader :as reader]
            [closh.zero.parser]
            [closh.zero.compiler]
            [closh.zero.platform.eval :as eval]
            [closh.zero.platform.clojure-compiler :as compiler])
  (:import (clojure.lang LineNumberingPushbackReader)))

(def read reader/read)

(defn eval [form]
  (eval/eval
   (closh.zero.compiler/compile-interactive
    (closh.zero.parser/parse form))))

;; Copied from clojure/core
(defn read+string
  "Like read, and taking the same args. stream must be a LineNumberingPushbackReader.
  Returns a vector containing the object read and the (whitespace-trimmed) string read."
  {:added "1.10"}
  ([] (read+string *in*))
  ([stream] (read+string stream true nil))
  ([stream eof-error? eof-value] (read+string stream eof-error? eof-value false))
  ([^clojure.lang.LineNumberingPushbackReader stream eof-error? eof-value recursive?]
   (try
     (.captureString stream)
     (let [o (read stream eof-error? eof-value recursive?)
           s (.trim (.getString stream))]
       [o s])
     (catch Throwable ex
       (.getString stream)
       (throw ex))))
  ([opts ^clojure.lang.LineNumberingPushbackReader stream]
   (try
     (.captureString stream)
     (let [o (read opts stream)
           s (.trim (.getString stream))]
       [o s])
     (catch Throwable ex
       (.getString stream)
       (throw ex)))))

(defn load-reader [rdr]
  (compiler/load rdr eval))

(defn compiler-load-file [path]
  (compiler/load-file path eval))

;; TODO: Does it make sense to reimplement this?
(defn rt-load-resource-script [path]
  (binding [*out* *err*]
    (println "rt-load-resource-script not implemented:" path))
  (System/exit 1))
