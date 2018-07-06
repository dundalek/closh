(ns closh.zero.pipeline
  (:require [closh.zero.platform.process :as process :refer [process?]]
            [closh.zero.platform.io :refer [out-stream in-stream err-stream stream-output pipe-stream line-seq stream-write *stdout* *stderr*]])
  (:refer-clojure :exclude [line-seq]))

(defn wait-for-pipeline
  "Wait for a pipeline to complete. Standard outputs of a process are piped to stdout and stderr."
  [proc]
  (if (process? proc)
    (do
      (when-let [stdout (out-stream proc)]
        (pipe-stream stdout *stdout*))
      (when-let [stderr (err-stream proc)]
        (pipe-stream stderr *stderr*))
      (process/wait proc))
    proc))

(defn pipeline-condition
  "Get status of a finished pipeline. Returns true if a process exited with non-zero code or a value is truthy."
  [proc]
  (if (process? proc)
    (zero? (process/exit-code proc))
    (boolean proc)))

(defn pipeline-value
  "Waits for a pipeline to finish and returns its output."
  [proc]
  (if (process? proc)
    (let [out (stream-output (out-stream proc))]
      (process/wait proc)
      @out)
    proc))

(defn process-output
  "Returns for a process to finish and returns output to be printed out."
  [proc]
  (if (seq? proc)
    (str (clojure.string/join "\n" proc) "\n")
    (pipeline-value proc)))

(defn process-value
  "Returns for a process to finish and returns map of exit code, stdout and stderr."
  [proc]
  (if (process? proc)
    (let [stdout (stream-output (out-stream proc))
          stderr (stream-output (err-stream proc))]
      (process/wait proc)
      {:stdout @stdout
       :stderr @stderr
       :code (process/exit-code proc)})
    {:stdout (str proc)
     :stderr ""
     :code 0}))

; TODO: refactor dispatch
(defn- pipe-internal
  "Pipes process or value to another process or function."
  ([from to]
   (cond
     (process? from)
     (cond
       (process? to)
       (do
         (when-let [in (in-stream to)]
           (let [out (or (out-stream from)
                         #?(:cljs (doto (stream.PassThrough.)
                                    (.end))))]
             (pipe-stream out in)))
         to)

       :else (to (process-output from)))

     (seq? from)
     (cond
       (process? to)
       (let [val (str (clojure.string/join "\n" from) "\n")]
         (stream-write (in-stream to) val)
         to)

       :else (to from))

     :else
     (cond
       (process? to)
       (do
         (when-let [stdin (in-stream to)]
           (stream-write stdin (str from)))
         to)

       :else (to from)))))

(defn pipe
  [x & xs]
  (reduce pipe-internal x xs))

(defn pipe-multi
  "Piping in multi mode. It splits streams and strings into seqs of strings by newline. Single value is wrapped in list. Then it is passed to `pipe`."
  [x f]
  (let [val (cond
              (process? x) (if-let [stream (out-stream x)]
                             (line-seq stream)
                             (list))
              (sequential? x) x
              (string? x) (clojure.string/split x #"\n")
              :else (list x))]
    (pipe val f)))

(defn pipe-map
  "Pipe by mapping a function."
  [proc f]
  (pipe-multi proc (partial map f)))

(defn pipe-filter
  "Pipe by filtering based on a function."
  [proc f]
  (pipe-multi proc (partial filter f)))
