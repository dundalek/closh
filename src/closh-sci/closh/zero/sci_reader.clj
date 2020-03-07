(ns closh.zero.sci-reader
  (:refer-clojure :exclude [read read-string])
  (:require [edamame.impl.parser :as parser]
            [clojure.tools.reader.edn :as edn]
            [clojure.tools.reader.reader-types :as r]))

(set! *warn-on-reflection* true)

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

(defn parse-next-custom [opts reader]
  (let [ctx (-> (assoc opts :all true)
              (parser/normalize-opts)
              (assoc ::parser/expected-delimiter nil))
        c (r/peek-char reader)]
    (let [loc (parser/location reader)
          obj (parser/dispatch ctx reader c)
          ret (if (identical? reader obj)
                (parser/parse-next ctx reader)
                (if (instance? clojure.lang.IObj obj)
                  (vary-meta obj merge loc)
                  obj))]
      (if (identical? ret ::parser/eof)
        (or (:eof opts) ::parser/eof)
        ret))))

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
                              (parse-next-custom opts reader))]
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
