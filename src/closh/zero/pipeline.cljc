(ns closh.zero.pipeline
  (:require [closh.zero.platform.process :as process :refer [process?]]
            [closh.zero.platform.io :refer [get-out-stream line-seq *stdout* *stderr*]]))

(defn wait-for-pipeline
  "Wait for a pipeline to complete. Standard outputs of a process are piped to stdout and stderr."
  [proc]
  (if (process? proc)
    (let [stdout (get-out-stream proc)]
      (when-let [stderr (.-stderr proc)]
        (.pipe stderr *stderr*))
      (.pipe stdout *stdout*)
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
    (let [out #js[]]
      (.on (get-out-stream proc) "data" #(.push out %))
      (process/wait proc)
      (.join out ""))
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
    (let [stdout #js[]
          stderr #js[]]
      (when-let [stream (.-stdout proc)] (.on stream "data" #(.push stdout %)))
      (when-let [stream (.-stderr proc)] (.on stream "data" #(.push stderr %)))
      (process/wait proc)
      {:stdout (.join stdout "")
       :stderr (.join stderr "")
       :code (process/exit-code proc)})
    {:stdout (str proc)
     :stderr ""
     :code 0}))

; TODO: refactor dispatch
(defn pipe
  "Pipes process or value to another process or function."
  ([from to]
   (cond
     (process? from)
     (cond
       (process? to)
       (do
         (when-let [stdin (.-stdin to)]
           (.pipe (get-out-stream from) stdin))
         to)

       :else (to (process-output from)))

     (seq? from)
     (cond
       (process? to)
       (let [val (str (clojure.string/join "\n" from) "\n")]
         (.write (.-stdin to) val)
         (.end (.-stdin to))
         to)

       :else (to from))

     :else
     (cond
       (process? to)
       (do
         (when-let [stdin (.-stdin to)]
           (.write stdin (str from))
           (.end stdin))
         to)

       :else (to from))))
  ([x & xs]
   (reduce pipe x xs)))

(defn pipe-multi
  "Piping in multi mode. It splits streams and strings into seqs of strings by newline. Single value is wrapped in list. Then it is passed to `pipe`."
  [x f]
  (let [val (cond
              (process? x) (line-seq (get-out-stream x))
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
