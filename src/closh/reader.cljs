(ns closh.reader
  (:require-macros [cljs.tools.reader.reader-types :refer [log-source]])
  (:require [cljs.tools.reader.reader-types :refer [string-push-back-reader unread read-char]]
            [cljs.tools.reader :refer [READ_FINISHED macros]]
            [cljs.tools.reader.impl.errors :as err]
            [cljs.tools.reader.impl.utils :refer [ws-rx]]
            [goog.array :as garray]))

(def ^:no-doc read-internal-orig cljs.tools.reader/read*-internal)

(defn- ^:no-doc ^boolean macro-terminating?
  "Customizes `cljs.tools.reader/macro-terminating?` so that read-token is more permissive. For example it does not stop on curly braces but reads them in so they can be used for brace expansion."
  [ch]
  (case ch
    ; (\" \; \@ \^ \` \~ \( \) \[ \] \{ \} \\) true
    (\" \; \( \) \[ \] \\) true
    false))

(defn- ^:no-doc ^boolean whitespace?
  "Customizes `cljs.tools.reader.impl.utils/whitespace?` so that read-token splits token only on whitespace and does not split on comma."
  [ch]
  (when-not (nil? ch)
    (.test ws-rx ch)))

(defn- ^:no-doc read-token
  "Reads a non-whitespace token. If it is a valid number it coerces it to number. Otherwise returns it as a symbol."
  [reader ch]
  (let [token (with-redefs [cljs.tools.reader/macro-terminating? macro-terminating?
                            cljs.tools.reader.impl.utils/whitespace? whitespace?]
                (apply cljs.tools.reader/read-token reader :symbol ch))
        number (js/Number token)]
    (if (js/isNaN number)
      (symbol token)
      number)))

(defn- ^:no-doc read-internal-custom
  "Customizes `cljs.tools.reader/read*-internal` with our reader enhancements."
  [^not-native reader ^boolean eof-error? sentinel return-on opts pending-forms]
  (with-redefs [cljs.tools.reader/read*-internal read-internal-orig]
   (loop []
     (log-source reader
       (if-not ^boolean (garray/isEmpty pending-forms)
         (let [form (aget pending-forms 0)]
           (garray/removeAt pending-forms 0)
           form)
         (let [ch (read-char reader)]
           (cond
             (whitespace? ch) (recur)
             (nil? ch) (if eof-error? (err/throw-eof-error reader nil) sentinel)
             (identical? ch return-on) READ_FINISHED
             ; (number-literal? reader ch) (read-number reader ch)
             (= \~ ch) (read-token reader ch)
             :else (let [f (macros ch)]
                     (if-not (nil? f)
                       (let [res (f reader ch opts pending-forms)]
                         (if (identical? res reader)
                           (recur)
                           res))
                       (read-token reader ch))))))))))

(defn- ^:no-doc read-orig
  "This is a verbatim copy `cljs.tools.reader/read`. We need a copy otherwise re-binding ends up in infinite loop."
  {:arglists '([] [reader] [opts reader] [reader eof-error? eof-value])}
  ([reader] (read-orig reader true nil))
  ([{eof :eof :as opts :or {eof :eofthrow}} reader] (cljs.tools.reader/read* reader (= eof :eofthrow) eof nil opts (to-array [])))
  ([reader eof-error? sentinel] (cljs.tools.reader/read* reader eof-error? sentinel nil {} (to-array []))))

(defn read
  "Replacement for a `cljs.tools.reader/read` which allows reading the command mode. It tries to read all input and returns a list of forms. Optionally takes a `transform` function which is called on the valid result."
  ([reader]
   (read {} reader))
  ([opts reader]
   (read opts reader identity))
  ([opts reader transform]
   (with-redefs [cljs.tools.reader/read*-internal read-internal-custom]
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
   (read opts reader #(conj % 'sh))))

(defn read-sh-value
  "Read input in command mode, wrap it in `sh-value` symbol."
  ([reader]
   (read-sh {} reader))
  ([opts reader]
   (read opts reader #(conj % 'sh-value))))
