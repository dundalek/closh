(ns closh.zero.reader
  (:require [cljs.tools.reader.reader-types :refer [string-push-back-reader unread read-char log-source]]
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
     (let [ret (log-source reader
                 (if-not ^boolean (garray/isEmpty pending-forms)
                   (let [form (aget pending-forms 0)]
                     (garray/removeAt pending-forms 0)
                     form)
                   (let [ch (read-char reader)]
                     (if-let [skip (when (= ch \\)
                                     (when-let [ch (read-char reader)]
                                       (if (= ch \newline)
                                         reader
                                         (do (unread reader ch)
                                           nil))))]
                       skip
                       (cond
                         (= ch \newline) \newline
                         (whitespace? ch) reader
                         (nil? ch) (if eof-error? (err/throw-eof-error reader nil) sentinel)
                         (identical? ch return-on) READ_FINISHED
                         ; (number-literal? reader ch) (read-number reader ch)
                         (= \~ ch) (read-token reader ch)
                         :else (if-let [f (macros ch)]
                                 (f reader ch opts pending-forms)
                                 (read-token reader ch)))))))]
        (if (identical? ret reader)
          (recur)
          ret)))))

(defn- ^:no-doc read-orig
  "This is a verbatim copy `cljs.tools.reader/read`. We need a copy otherwise re-binding ends up in infinite loop."
  {:arglists '([] [reader] [opts reader] [reader eof-error? eof-value])}
  ([reader] (read-orig reader true nil))
  ([{eof :eof :as opts :or {eof :eofthrow}} reader] (cljs.tools.reader/read* reader (= eof :eofthrow) eof nil opts (to-array [])))
  ([reader eof-error? sentinel] (cljs.tools.reader/read* reader eof-error? sentinel nil {} (to-array []))))

(defn read
  "Replacement for a `cljs.tools.reader/read` which allows reading the command mode. It tries to read all input on the line and returns a list of forms. Optionally takes a `transform` function which is called on the valid result."
  ([reader]
   (read {} reader))
  ([opts reader]
   (read opts reader identity))
  ([opts reader transform]
   (with-redefs [cljs.tools.reader/read*-internal read-internal-custom]
     (loop [coll (transient [])]
       (let [ch (read-char reader)]
         (cond
           (nil? ch)
           (if-let [result (seq (persistent! coll))]
             (transform result)
             (read-orig opts reader))

           (and (not= ch \newline) (whitespace? ch)) (recur coll)

           :else (do
                   (unread reader ch)
                   (let [token (read-orig opts reader)]
                     (if (or (= token \;)
                             (= token \newline)
                             (and (:eof opts) (= token (:eof opts))))
                       (if-let [result (seq (persistent! coll))]
                         (transform result)
                         (recur (transient [])))
                       (recur (conj! coll token)))))))))))

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
   (read opts reader #(conj % 'sh-value))))

(defn read-all [rdr]
 (let [eof #()
       opts {:eof eof :read-cond :allow :features #{:cljs}}]
   (loop [forms []]
      (let [form (read opts rdr)]
        (if (= form eof)
          (seq forms)
          (recur (conj forms form)))))))

(defn read-transform [rdr]
  (->> (read-all rdr)
    (map #(pr-str (conj % 'closh.zero.macros/sh-wrapper)))
    (clojure.string/join "\n")))
