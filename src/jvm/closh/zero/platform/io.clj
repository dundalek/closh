(ns closh.zero.platform.io
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [org.satta.glob :as clj-glob])
  (:refer-clojure :exclude [line-seq]))

(def ^:dynamic *stdin* System/in)
(def ^:dynamic *stdout* System/out)
(def ^:dynamic *stderr* System/err)

(set! *warn-on-reflection* true)

(def ^:private relpath-regex #"^\./")

#_(defn glob
  ([s] (glob s nil))
  ([s ^String cwd-file]
   (let [pattern (str/replace s relpath-regex "")
         is-relative (not= s pattern)
         result (clj-glob/glob pattern (java.io.File. cwd-file))]
     (if (seq result)
       (for [item result]
         (let [s (str item)
               path (if (str/starts-with? s cwd-file)
                      (subs s (inc (count cwd-file)))
                      s)]
           (if is-relative
             (str "./" path)
             path)))
       (list s)))))

(defn- directory? [path]
  (java.nio.file.Files/isDirectory path (into-array java.nio.file.LinkOption [])))

(defn glob
  ([pattern] (glob pattern nil))
  ([pattern ^String cwd]
   (let [cwd       (java.nio.file.Paths/get cwd (into-array String []))
         separator (java.io.File/separator)
         result    (->> (str/split pattern (re-pattern separator))
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
                                             (update :path #(.getParent ^java.nio.file.Path %))
                                             (update :parts conj ".."))))
                                  (filter (fn [{:keys [path parts]}]
                                            (some? path))))

                             (->> acc
                                  (filter (fn [{:keys [path parts]}]
                                            (directory? path)))
                                  (mapcat (fn [{:keys [^java.nio.file.Path path parts]}]
                                            (map (fn [^java.nio.file.Path nested-path]
                                                   {:path  nested-path
                                                    :parts (conj parts (str (.getFileName nested-path)))})
                                                 (java.nio.file.Files/newDirectoryStream path segment))))
                                  (filter (fn [{:keys [^java.nio.file.Path path parts]}]
                                            (and (some? path)
                                                 (or (str/starts-with? segment ".")
                                                     (not (str/starts-with? (.getFileName path) ".")))))))))
                         [{:path cwd, :parts []}])
                        (map (fn [{:keys [parts]}]
                               (str/join separator parts)))
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
