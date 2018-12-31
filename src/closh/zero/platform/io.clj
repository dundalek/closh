(ns closh.zero.platform.io
  (:require [clojure.string]
            [clojure.java.io :as io]
            [org.satta.glob :as clj-glob])
  (:refer-clojure :exclude [line-seq]))

(def ^:dynamic *stdin* System/in)
(def ^:dynamic *stdout* System/out)
(def ^:dynamic *stderr* System/err)

(def ^:private relpath-regex #"^\./")

(defn output-stream? [stream]
  (instance? java.io.OutputStream stream))

(defn glob
  ([s] (glob s nil))
  ([s cwd-file]
   (let [pattern (clojure.string/replace s relpath-regex "")
         is-relative (not= s pattern)
         result (clj-glob/glob pattern (java.io.File. cwd-file))]
      (if (seq result)
        (let [len (count cwd-file)]
          (map
            #(let [s (str %)
                   path (if (= cwd-file (subs s 0 len))
                          (subs s (inc len))
                          s)]
               (if is-relative
                 (str "./" path)
                 path))
            result))
        [s]))))

(defn out-stream
  "Get stdout stream of a given process."
  [proc]
  (.getInputStream proc))

(defn in-stream [proc]
  (.getOutputStream proc))

(defn err-stream [proc]
  (.getErrorStream proc))

(defn pipe-stream [from to]
  ; (.start (Thread. (fn [] (io/copy from to))))
  (try
    (io/copy from to)
    (when (and (not= to *stderr*)
               (not= to *stdout*))
      (.close to))
    (catch java.io.IOException e
      (when-not (= (:cause (Throwable->map e)) "Stream closed")
        (throw e)))))

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

(defn output-stream [filename]
  (io/output-stream filename))
