(ns closh.zero.reader
  (:refer-clojure :exclude [read read-string])
  (:require [clojure.tools.reader.reader-types :as r]
            #?(:cljs [closh.zero.cljs-reader :as reader])))

#?(:clj
   (defmacro require-reader []
        (if (System/getenv "__CLOSH_USE_SCI_EVAL__")
          (list 'require ''[closh.zero.sci-reader :as reader])
          (list 'require ''[closh.zero.clojure-reader :as reader]))))

#?(:clj (require-reader))

(def read reader/read)
(def read-all reader/read-all)

(defn string-reader
  "Create reader for strings."
  [s]
  (r/indexing-push-back-reader
   (r/string-push-back-reader s)))

#?(:clj
   (do
     (defn read-sh [& args]
       (cons 'closh.zero.macros/sh (apply read args)))

     (defn read-sh-value [& args]
       (cons 'closh.zero.macros/sh-value (apply read args))))
   :cljs
   (do
     (defn read-sh
       "Read input in command mode, wrap it in `sh` symbol."
       ([reader]
        (read-sh {} reader))
       ([opts reader]
        (read opts reader #(conj % 'closh.zero.macros/sh))))

     (defn read-sh-value
       "Read input in command mode, wrap it in `sh-value` symbol."
       ([reader]
        (read-sh {} reader))
       ([opts reader]
        (read opts reader #(conj % 'sh-value))))))

(defn read-string [s]
  (read (string-reader s)))

(defn read-string-all [s]
  (read-all (string-reader s)))
