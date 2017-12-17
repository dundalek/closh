(ns closh.reader
  (:require-macros [cljs.tools.reader.reader-types :refer [log-source]])
  (:require [cljs.tools.reader.reader-types :refer [string-push-back-reader unread read-char]]
            [cljs.tools.reader :refer [READ_FINISHED macros read-symbol]]
            [cljs.tools.reader.impl.errors :as err]
            [cljs.tools.reader.impl.utils :refer [ws-rx]]
            [goog.array :as garray]))

(def read-internal-orig cljs.tools.reader/read*-internal)

(defn- ^boolean macro-terminating? [ch]
  (case ch
    ; (\" \; \@ \^ \` \~ \( \) \[ \] \{ \} \\) true
    (\" \; \( \) \[ \] \\) true
    false))

(defn ^boolean whitespace?
  "Checks whether a given character is whitespace"
  [ch]
  (.test ws-rx ch))

(defn read-token [reader ch]
  (binding [cljs.tools.reader/macro-terminating? macro-terminating?
            cljs.tools.reader.impl.utils/whitespace? whitespace?]
    (cljs.tools.reader/read-token reader :symbol ch)))

(defn read-internal-custom
  [^not-native reader ^boolean eof-error? sentinel return-on opts pending-forms]
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
                      (binding [cljs.tools.reader/read*-internal read-internal-orig]
                        (let [res (f reader ch opts pending-forms)]
                          (if (identical? res reader)
                            (recur)
                            res)))
                      (read-token reader ch)))))))))
                        ; (read-symbol reader ch)))))))))


(defn read-orig
  {:arglists '([] [reader] [opts reader] [reader eof-error? eof-value])}
  ([reader] (readx reader true nil))
  ([{eof :eof :as opts :or {eof :eofthrow}} reader] (cljs.tools.reader/read* reader (= eof :eofthrow) eof nil opts (to-array [])))
  ([reader eof-error? sentinel] (cljs.tools.reader/read* reader eof-error? sentinel nil {} (to-array []))))

(defn read
  ([reader]
   (read {} reader))
  ([opts reader]
   (binding [cljs.tools.reader/read*-internal read-internal-custom]
     (loop [coll (transient [])]
       (let [[item exception] (try
                                [(read-orig opts reader) nil]
                                (catch :default e [nil e]))]
         (if (or exception
                 (and (:eof opts) (= (:eof opts) item)))
           (if-let [result (seq (persistent! coll))]
             (conj result 'sh)
             item)
           (recur (conj! coll item))))))))

(defn read-string
  ([s]
   (read-string {} s))
  ([opts s]
   (read opts (string-push-back-reader s))))
