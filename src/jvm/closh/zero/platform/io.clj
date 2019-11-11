(ns closh.zero.platform.io
  (:require [clojure.string :as str]
            [clojure.java.io :as io])
  (:refer-clojure :exclude [line-seq])
  (:import [java.nio.file Files Paths Path]))

(def ^:dynamic *stdin* System/in)
(def ^:dynamic *stdout* System/out)
(def ^:dynamic *stderr* System/err)

(set! *warn-on-reflection* true)

(defn- directory? [path]
  (java.nio.file.Files/isDirectory path (into-array java.nio.file.LinkOption [])))

(defn- ^Path to-path [s]
  (Paths/get s (into-array String [])))

(defn glob
  ([pattern] (glob pattern nil))
  ([pattern ^String cwd]
   (let [absolute? (.isAbsolute (to-path pattern))
         cwd       (if absolute?
                     (.getRoot (to-path pattern))
                     (to-path cwd))
         separator (java.io.File/separator)
         result    (->> (str/split pattern (re-pattern separator))
                        (remove str/blank?)
                        (reduce
                         (fn [acc ^String segment]
                           (case segment
                             "."
                             (map #(update % :parts conj ".") acc)

                             ".."
                             (->> acc
                                  (filter (fn [{:keys [path parts]}]
                                            (directory? path)))
                                  (map (fn [entry]
                                         (-> entry
                                             (update :path #(.getParent ^Path %))
                                             (update :parts conj ".."))))
                                  (filter (fn [{:keys [path parts]}]
                                            (some? path))))

                             (->> acc
                                  (filter (fn [{:keys [path parts]}]
                                            (directory? path)))
                                  (mapcat (fn [{:keys [^Path path parts]}]
                                            (map (fn [^Path nested-path]
                                                   {:path  nested-path
                                                    :parts (conj parts (str (.getFileName nested-path)))})
                                                 (Files/newDirectoryStream path segment))))
                                  (filter (fn [{:keys [^Path path parts]}]
                                            (and (some? path)
                                                 (or (str/starts-with? segment ".")
                                                     (not (str/starts-with? (.getFileName path) ".")))))))))
                         [{:path cwd, :parts []}])
                        (map (fn [{:keys [parts]}]
                               (if absolute?
                                 ;; cwd is root in this case
                                 (str cwd (str/join separator parts))
                                 (str/join separator parts))))
                        sort)]
     (if (seq result)
       result
       (list pattern)))))


(defn out-stream
  "Get stdout stream of a given process."
  ^java.io.InputStream [^Process proc]
  (.getInputStream proc))

(defn in-stream ^java.io.OutputStream [^Process proc]
  (.getOutputStream proc))

(defn err-stream ^java.io.InputStream [^Process proc]
  (.getErrorStream proc))

(defn pipe-stream [from to]
  ; (.start (Thread. (fn [] (io/copy from to))))
  (try
    (io/copy from to)
    (when (and (not= to *stderr*)
               (not= to *stdout*))
      (.close ^java.io.Closeable to))
    (catch java.io.IOException e
      (when-not (= (:cause (Throwable->map e)) "Stream closed")
        (throw e)))))

(defn stream-output
  "Returns for a process to finish and returns output to be printed out."
  [stream]
  (with-open [writer (java.io.StringWriter.)]
    (pipe-stream stream writer)
    (delay (str writer))))

(defn stream-write [stream ^String val]
  (with-open [writer (java.io.OutputStreamWriter. stream)]
    (.write writer val)))

(defn line-seq [stream]
  (-> stream
      (java.io.InputStreamReader.)
      (java.io.BufferedReader.)
      (clojure.core/line-seq)))

(defn input-stream? [stream]
  (instance? java.io.InputStream stream))

(defn output-stream? [stream]
  (instance? java.io.OutputStream stream))

(defn input-stream [& args]
  (apply io/input-stream args))

(defn output-stream [& args]
  (apply io/output-stream args))
