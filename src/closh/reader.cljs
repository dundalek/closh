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

(defn read-string
  ([s]
   (read-string {:eof ::eof} s))
  ([opts s]
   (let [reader (string-push-back-reader s)]
     (binding [cljs.tools.reader/read*-internal read-internal-custom]
       (loop [coll (transient [])]
         (let [item (cljs.tools.reader/read opts reader)]
           (if (= item ::eof)
             (seq (persistent! coll))
             (recur (conj! coll item)))))))))
