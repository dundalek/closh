(ns closh.zero.reader
  (:refer-clojure :exclude [read read-string])
  (:require [clojure.tools.reader.reader-types :as r]))

(defmacro require-reader []
  (if (System/getenv "__CLOSH_USE_SCI_EVAL__")
    (list 'require ''[closh.zero.sci-reader :as reader])
    (list 'require ''[closh.zero.clojure-reader :as reader])))

(require-reader)

(def read reader/read)
(def read-all reader/read-all)

(defn string-reader
  "Create reader for strings."
  [s]
  (r/indexing-push-back-reader
   (r/string-push-back-reader s)))

(defn read-sh [rdr]
  (cons 'closh.zero.macros/sh (read rdr)))

(defn read-sh-value [rdr]
  (cons 'closh.zero.macros/sh-value (read rdr)))

(defn read-string [s]
  (read (string-reader s)))

(defn read-string-all [s]
  (read-all (string-reader s)))
