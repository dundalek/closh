(ns closh.reader
  (:refer-clojure :exclude [read read-line read-string char
                            default-data-readers *default-data-reader-fn*
                            *read-eval* *data-readers* *suppress-read*])
  (:require [clojure.tools.reader.reader-types :refer [log-source string-push-back-reader unread read-char indexing-reader? get-line-number get-column-number get-file-name source-logging-reader?]]
            [clojure.tools.reader :refer [READ_FINISHED macros *read-eval*]]
            [clojure.tools.reader.impl.utils :refer :all]
            [clojure.tools.reader.impl.errors :as err])
  (:import (java.util List LinkedList)))

(def ^:no-doc read*-orig clojure.tools.reader/read*)

(defn- ^:no-doc ^boolean macro-terminating?
  "Customizes `clojure.tools.reader/macro-terminating?` so that read-token is more permissive. For example it does not stop on curly braces but reads them in so they can be used for brace expansion."
  [ch]
  (case ch
    ; (\" \; \@ \^ \` \~ \( \) \[ \] \{ \} \\) true
    (\" \; \( \) \[ \] \\) true
    false))

(defn- ^:no-doc ^boolean whitespace?-custom
  "Customizes `clojure.tools.reader.impl.utils/whitespace?` so that read-token splits token only on whitespace and does not split on comma."
  [ch]
  (when ch
    (Character/isWhitespace ^Character ch)))

(defn- ^:no-doc read-token
  "Reads a non-whitespace token. If it is a valid number it coerces it to number. Otherwise returns it as a symbol."
  [reader ch]
  (let [token (with-redefs [clojure.tools.reader/macro-terminating? macro-terminating?
                            clojure.tools.reader.impl.utils/whitespace? whitespace?-custom]
                (clojure.tools.reader/read-token reader :symbol ch))]
    (try
      (Integer/parseInt token)
      (catch Exception _
        (symbol token)))))

(defn- ^:no-doc read-custom
  "Customizes `clojure.tools.reader/read*` with our reader enhancements."
  ([reader eof-error? sentinel opts pending-forms]
   (read-custom reader eof-error? sentinel nil opts pending-forms))
  ([reader eof-error? sentinel return-on opts pending-forms]
   (when (= :unknown *read-eval*)
     (err/reader-error "Reading disallowed - *read-eval* bound to :unknown"))
   (try
     (loop []
       (let [ret (log-source reader
                   (if (seq pending-forms)
                     (.remove ^List pending-forms 0)
                     (let [ch (read-char reader)]
                       (cond
                         (whitespace?-custom ch) reader
                         (nil? ch) (if eof-error? (err/throw-eof-error reader nil) sentinel)
                         (= ch return-on) READ_FINISHED
                         ; (number-literal? reader ch) (read-number reader ch)
                         (= \~ ch) (read-token reader ch)
                         :else (if-let [f (macros ch)]
                                 (with-redefs [clojure.tools.reader/read* read*-orig]
                                   (f reader ch opts pending-forms))
                                 (read-token reader ch))))))]
         (if (identical? ret reader)
           (recur)
           ret)))
    (catch Exception e
      (if (ex-info? e)
        (let [d (ex-data e)]
          (if (= :reader-exception (:type d))
            (throw e)
            (throw (ex-info (.getMessage e)
                            (merge {:type :reader-exception}
                                   d
                                   (if (indexing-reader? reader)
                                     {:line   (get-line-number reader)
                                      :column (get-column-number reader)
                                      :file   (get-file-name reader)}))
                            e))))
        (throw (ex-info (.getMessage e)
                        (merge {:type :reader-exception}
                               (if (indexing-reader? reader)
                                 {:line   (get-line-number reader)
                                  :column (get-column-number reader)
                                  :file   (get-file-name reader)})))))))))

(defn- ^:no-doc read-orig
  "This is a verbatim copy `clojure.tools.reader/read`. We need a copy otherwise re-binding ends up in infinite loop."
  {:arglists '([] [reader] [opts reader] [reader eof-error? eof-value])}
  ([reader] (read-orig reader true nil))
  ([{eof :eof :as opts :or {eof :eofthrow}} reader] (clojure.tools.reader/read* reader (= eof :eofthrow) eof nil opts (to-array [])))
  ([reader eof-error? sentinel] (clojure.tools.reader/read* reader eof-error? sentinel nil {} (to-array []))))

(defn read
  "Replacement for a `clojure.tools.reader/read` which allows reading the command mode. It tries to read all input and returns a list of forms. Optionally takes a `transform` function which is called on the valid result."
  ([reader]
   (read {} reader))
  ([opts reader]
   (read opts reader identity))
  ([opts reader transform]
   (with-redefs [clojure.tools.reader/read* read-custom]
     (loop [coll (transient [])]
       (let [ch (read-char reader)]
         (cond
           (nil? ch) (if-let [result (seq (persistent! coll))]
                       (transform result)
                       (read-orig opts reader))
           (whitespace? ch) (recur coll)
           :else (do (unread reader ch)
                     (recur (conj! coll (read-orig opts reader))))))))))

(defn read-sh
  "Read input in command mode, wrap it in `sh` symbol."
  ([reader]
   (read-sh {} reader))
  ([opts reader]
   (read opts reader #(conj % 'closh.macros/sh))))

(defn read-sh-value
  "Read input in command mode, wrap it in `sh-value` symbol."
  ([reader]
   (read-sh {} reader))
  ([opts reader]
   (read opts reader #(conj % 'closh.macros/sh-value))))
