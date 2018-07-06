(ns closh.zero.platform.io
  (:require [clojure.string]
            [clojure.java.io :as io]
            [org.satta.glob :as clj-glob])
  (:refer-clojure :exclude [line-seq]))

(def ^:dynamic *stdin* System/in)
(def ^:dynamic *stdout* System/out)
(def ^:dynamic *stderr* System/err)

(def ^:private relpath-regex #"^\./")

(defn glob [s]
  (let [pattern (clojure.string/replace s relpath-regex "")
        result (clj-glob/glob pattern)]
    (if (seq result)
      (if (= s pattern)
        (map #(clojure.string/replace (str %) relpath-regex "") result)
        (map #(str %) result))
      [s])))

(defn out-stream
  "Get stdout stream of a given process."
  [proc]
  (.getInputStream proc))

(defn in-stream [proc]
  (.getOutputStream proc))

(defn err-stream [proc]
  (.getErrorStream proc))

(defn pipe-stream [from to]
  (io/copy from to)
  (.close to))

(defn stream-output
  "Returns for a process to finish and returns output to be printed out."
  [stream]
  (with-open [writer (java.io.StringWriter.)]
    (pipe-stream stream writer)
    (delay (str writer))))

(defn stream-write [stream val]
  (with-open [writer (java.io.OutputStreamWriter. stream)]
    (.write writer val)))

(defn line-seq [stream]
  (-> stream
    (java.io.InputStreamReader.)
    (java.io.BufferedReader.)
    (clojure.core/line-seq)))
