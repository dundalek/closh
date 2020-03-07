(ns closh.zero.reader
  (:refer-clojure :exclude [read read-string])
  (:require [clojure.tools.reader.reader-types :as r]
            [clojure.tools.reader.edn :as edn]
            #?(:cljs [closh.zero.cljs-reader :as reader])))

(set! *warn-on-reflection* true)

#?(:clj
   (defmacro require-reader []
     (if (System/getenv "__CLOSH_USE_SCI_EVAL__")
       '(require '[closh.zero.sci-reader :refer [read-clojure]])
       '(do (require 'clojure.tools.reader)
            (def read-clojure clojure.tools.reader/read)))))

#?(:clj (require-reader))

(defn whitespace?-custom
  "Customizes `clojure.tools.reader.impl.utils/whitespace?` so that read-token splits token only on whitespace and does not split on comma."
  [ch]
  (and ch (Character/isWhitespace ^Character ch)))

(defn macro-terminating?
  "Customizes `clojure.tools.reader/macro-terminating?` so that read-token is more permissive. For example it does not stop on curly braces but reads them in so they can be used for brace expansion."
  [ch]
  (case ch
    ; (\" \; \@ \^ \` \~ \( \) \[ \] \{ \} \\) true
    (\" \; \( \) \[ \] \\) true
    false))

(defn token-start? [c]
  (case c
    ;; \~ is considered as token start
    (nil \# \@ \' \` \( \[ \{ \} \] \) \;) false
    true))

(defn ^String read-token*
  "Read in a single logical token from the reader"
  [rdr]
  (loop [sb (StringBuilder.) ch (r/read-char rdr)]
    (if (or (whitespace?-custom ch)
            (macro-terminating? ch)
            (nil? ch))
      (do (when ch
            (r/unread rdr ch))
          (str sb))
      (recur (.append sb ch) (r/read-char rdr)))))

(defn read-token [rdr]
  (if (= \" (r/peek-char rdr))
    (edn/read rdr)
    (let [token (read-token* rdr)]
      (try
        (Integer/parseInt token)
        (catch Exception _
          (symbol token))))))

(defn read* [opts reader]
  (loop [coll (transient [])]
    (let [ch (r/read-char reader)]
      (cond
        (nil? ch)
        (if-some [result (seq (persistent! coll))]
          result
          (:eof opts))

        (and (= ch \\) (= \; (r/peek-char reader)))
        (do
          (r/read-char reader)
          (if-let [result (seq (persistent! coll))]
            result
            (recur (transient []))))

        (and (= ch \\) (= \newline (r/peek-char reader)))
        (do
          (r/read-char reader)
          (recur coll))

        (or (= ch \newline))
        (if-let [result (seq (persistent! coll))]
          result
          (recur (transient [])))

        (whitespace?-custom ch) (recur coll)

        :else (do
                (r/unread reader ch)
                (let [token (if (token-start? ch)
                              (read-token reader)
                              (read-clojure opts reader))]
                  (if (and (:eof opts)
                           (identical? token (:eof opts)))
                    (if-let [result (seq (persistent! coll))]
                      result
                      (:eof opts))
                    (recur (conj! coll token)))))))))

(defn read
  ([]
   (read *in*))
  ([stream]
   (read stream true nil))
  ([stream eof-error? eof-value]
   (read stream eof-error? eof-value false))
  ([stream eof-error? eof-value recursive?]
   ;; TODO what does `recursive?` do?
   (read* {:features #{:clj} :read-cond :allow :eof eof-value} stream))
  ([opts stream]
   (read* opts stream)))

(defn read-all [rdr]
  (let [eof #()
        opts {:eof eof :read-cond :allow :features #{#?(:clj :clj :cljs :cljs)}}]
    (loop [forms (transient [])]
      (let [form (read opts rdr)]
        (if (identical? form eof)
          (seq (persistent! forms))
          (recur (conj! forms form)))))))

(defn string-reader
  "Create reader for strings."
  [s]
  (r/indexing-push-back-reader
   (r/string-push-back-reader s)))

(defn read-sh
  "Read input in command mode, wrap it in `sh` symbol."
  ([reader]
   (read-sh {} reader))
  ([opts reader]
   (let [value (read opts reader)]
     (if (and (:eof opts) (identical? value (:eof opts)))
       value
       (cons 'closh.zero.macros/sh value)))))

(defn read-sh-value
  "Read input in command mode, wrap it in `sh-value` symbol."
  ([reader]
   (read-sh {} reader))
  ([opts reader]
   (let [value (read opts reader)]
     (if (and (:eof opts) (identical? value (:eof opts)))
       value
       (cons 'closh.zero.macros/sh-value value)))))

(defn read-string [s]
  (read (string-reader s)))

(defn read-string-all [s]
  (read-all (string-reader s)))
