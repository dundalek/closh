(ns closh.zero.platform.process
  (:require [clojure.java.io :as io :refer [default-streams-impl make-input-stream make-output-stream IOFactory]]
            [clojure.string :as str]
            [closh.zero.platform.io :refer [*stdout* *stderr*]])
  (:import (java.io File)
           (java.net URL MalformedURLException)))

(set! *warn-on-reflection* true)

(def ^:dynamic *env* (atom {}))

(def ^:dynamic *cwd* (atom (System/getProperty "user.dir")))

(defn process? [proc]
  (instance? Process proc))

(defn exit-code [^Process proc]
  (.exitValue proc))

(defn wait
  "Wait untils process exits and all of its stdio streams are closed."
  [proc]
  (.waitFor ^Process proc)
  proc)

(defn exit [code]
  (System/exit code))

(defn cwd ^String []
  @*cwd*)

(defn resolve-file ^File [^String s]
  (let [f (File. s)]
    (if (.isAbsolute f) f (File. (cwd) s))))

(defn resolve-path ^String [s]
  (.getCanonicalPath (resolve-file s)))

(defn chdir [dir]
  (let [target (resolve-path dir)]
    (if (.isDirectory (File. target))
      (reset! *cwd* target)
      (throw (Exception. (str target ": Is not a directory"))))))

(defn setenv [k v]
  (let [val (str v)]
    (swap! *env* assoc k val)
    val))

(defn unsetenv [k]
  (swap! *env* dissoc k))

(defn getenv
  ([] (merge
       (into {} (System/getenv))
       @*env*))
  ([k] (if (contains? @*env* k)
         (get @*env* k)
         (System/getenv k))))

(defn- executable? [^java.io.File file]
  (and (.isFile file) (.canExecute file)))

(defn find-executable
  ([name] (find-executable name (getenv "PATH")))
  ([name path]
   (if (and (str/includes? name File/separator)
            (executable? (resolve-file name)))
     name
     (let [^String path (or path "")]
       (->> (.split path File/pathSeparator) ;; why not use str/split?
            (keep (fn [dirname]
                    (let [file (io/file dirname name)]
                      (when (executable? file)
                        (.getAbsolutePath file)))))
            (first))))))

(defn shx
  "Executes a command as child process."
  ([cmd] (shx cmd []))
  ([cmd args] (shx cmd args {}))
  ([cmd args opts]
   (let [builder (ProcessBuilder. ^java.util.List (map str (concat [(some-> cmd find-executable)] (flatten args))))
         std-flip (atom false)
         redirects
         (reduce
          (fn [redirects [op fd target]]
            (case op
              :rw (throw (Exception. "Read/Write redirection is not supported"))
              (let [redirect (case op
                               :in (java.lang.ProcessBuilder$Redirect/from (File. ^String target))
                               :out (java.lang.ProcessBuilder$Redirect/to (File. ^String target))
                               :append (java.lang.ProcessBuilder$Redirect/appendTo (File. ^String target))
                               :set (if (#{:stdin :stdout :stderr} target)
                                      (case target
                                        :stdin java.lang.ProcessBuilder$Redirect/INHERIT
                                        :stdout (if (= *stdout* System/out) java.lang.ProcessBuilder$Redirect/INHERIT target)
                                        :stderr (if (= *stderr* System/err) java.lang.ProcessBuilder$Redirect/INHERIT target))
                                      (get redirects target)))]
                (if redirect
                  (assoc redirects fd redirect)
                  redirects))))
          {0 :stdin 1 :stdout 2 :stderr}
          (:redir opts))]
     (doseq [[fd ^java.lang.ProcessBuilder$Redirect target] redirects]
       (let [redir (if (#{:stdin :stdout :stderr} target)
                     java.lang.ProcessBuilder$Redirect/PIPE
                     target)]
         (case (long fd)
           0 (.redirectInput builder redir)
           1 (if (= target :stderr)
               (reset! std-flip true)
               (.redirectOutput builder redir))
           2 (if (= target :stdout)
               (.redirectErrorStream builder true)
               (.redirectError builder redir))
           (throw (Exception. (str "Unsupported file descriptor: " fd " (only file descriptors 0, 1, 2 are supported)"))))))
     (let [env (.environment builder)]
       (.clear env)
       (doseq [[k v] (getenv)]
         (.put env k v)))
     (.directory builder (File. (cwd)))
     (let [process (.start builder)]
       (if @std-flip
         (proxy [java.lang.Process] []
           (destroy []
             (.destroy process))
           (destroyForcibly []
             (.destroyForcibly process))
           (exitValue []
             (.exitValue process))
           (getErrorStream []
             (.getInputStream process))
           (getInputStream []
             (.getErrorStream process))
           (getOutputStream []
             (.getOutputStream process))
           (isAlive []
             (.isAlive process))
           (waitFor
             ([] (.waitFor process))
             ([timeout unit] (.waitFor process timeout unit))))
         process)))))

;; Extend protocols to make IO functions aware of the CWD, e.g. for slurp
(extend String
  IOFactory
  (assoc default-streams-impl
         :make-input-stream (fn [^String x opts]
                              (try
                                (make-input-stream (URL. x) opts)
                                (catch MalformedURLException e
                                  (make-input-stream (resolve-file x) opts))))
         :make-output-stream (fn [^String x opts]
                               (try
                                 (make-output-stream (URL. x) opts)
                                 (catch MalformedURLException err
                                   (make-output-stream (resolve-file x) opts))))))
