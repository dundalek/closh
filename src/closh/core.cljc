(ns closh.core
  (:require [clojure.string]
            [goog.object :as gobj]
            [closh.builtin :refer [getenv]]
            [closh.env :refer [*closh-aliases* *closh-abbreviations*]]))

(def ^:no-doc fs (js/require "fs"))
(def ^:no-doc child-process (js/require "child_process"))
(def ^:no-doc stream (js/require "stream"))
(def ^:no-doc glob (.-sync (js/require "glob")))
(def ^:no-doc deasync (js/require "deasync"))

(def command-not-found-bin "/usr/lib/command-not-found")

(def ^:dynamic *stdin* js/process.stdin)
(def ^:dynamic *stdout* js/process.stdout)
(def ^:dynamic *stderr* js/process.stderr)

(defn expand-variable
  "Expands env variable, it does not look inside string."
  [s]
  (if (re-find #"^\$" s)
    (getenv (subs s 1))
    s))

(defn expand-tilde
  "Expands tilde character to a path to user's home directory."
  [s]
  (clojure.string/replace-first s #"^~" (getenv "HOME")))

(defn expand-filename
  "Expands filename based on globbing patterns"
  [s]
  (seq (glob s #js{:nonull true})))

(defn expand-redirect
  "Expand redirect targets. It does tilde and variable expansion."
  [s]
  (-> s
      (expand-tilde)
      (expand-variable)))

; Bash: Partial quote (allows variable and command expansion)
(defn expand-partial
  "Partially expands parameter which is used when parameter is quoted as string. It only does variable expansion."
  [s]
  (if-let [result (expand-variable s)]
    (list result)
    (list)))

; Bash: The order of expansions is: brace expansion; tilde expansion, parameter and variable expansion, arithmetic expansion, and command substitution (done in a left-to-right fashion); word splitting; and filename expansion.
(defn expand
  "Expands command-line parameter.

  The order of expansions is variable expansion, tilde expansion and filename expansion."
  [s]
  (if-let [x (expand-variable s)]
    (-> x
      expand-tilde
      expand-filename)
    (list)))

(defn line-seq
  "Create a lazy seq of strings from stream"
  ([stream]
   (let [buf #js[]
         done (atom false)]
      (doto stream
        (.on "end" #(reset! done true))
        (.on "data" #(.push buf %)))
      (line-seq (fn []
                  (when-not @done
                    (.loopWhile deasync #(and (not @done) (empty? buf)))
                    (.shift buf)))
        nil)))
  ([read-chunk line]
   (if-let [chunk (read-chunk)]
     (if (re-find #"\n" (str line chunk))
       (let [lines (.split (str line chunk) "\n")]
         (if (= 1 (count lines))
           (lazy-cat lines (line-seq read-chunk nil))
           (lazy-cat (butlast lines) (line-seq read-chunk (last lines)))))
       (recur read-chunk (str line chunk)))
     (if (not (empty? line))
       (list line)
       (list)))))

(defn get-out-stream
  "Get stdout stream of a given process or empty stream"
  [proc]
  (if-let [stdout (.-stdout proc)]
    stdout
    (let [s (stream.PassThrough.)]
      (.end s)
      s)))

(defn wait-for-event
  "Synchronously wait for an event to be trigerred on an event emitter."
  [emitter event]
  (let [done (atom false)]
    (.on emitter event #(reset! done {:val %}))
    (.loopWhile deasync #(not @done))
    (:val @done)))

(defn wait-for-process
  "Wait untils process exits and all of its stdio streams are closed."
  [proc]
  (when (and (instance? child-process.ChildProcess proc)
             (nil? (.-exitCode proc)))
    (wait-for-event proc "close"))
  proc)

(defn wait-for-pipeline
  "Wait for a pipeline to complete. Standard outputs of a process are piped to stdout and stderr."
  [proc]
  (if (instance? child-process.ChildProcess proc)
    (let [stdout (get-out-stream proc)]
      (when-let [stderr (and proc (.-stderr proc))]
        (.pipe stderr *stderr*))
      (.pipe stdout *stdout*)
      (wait-for-process proc))
    proc))

(defn pipeline-condition
  "Get status of a finished pipeline. Returns true if a process exited with non-zero code or a value is truthy."
  [proc]
  (if (instance? child-process.ChildProcess proc)
    (zero? (.-exitCode proc))
    (boolean proc)))

(defn pipeline-value
  "Waits for a pipeline to finish and returns its output."
  [proc]
  (if (instance? child-process.ChildProcess proc)
    (let [out #js[]]
      (.on (get-out-stream proc) "data" #(.push out %))
      (wait-for-process proc)
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
  (if (instance? child-process.ChildProcess proc)
    (let [stdout #js[]
          stderr #js[]]
      (when-let [stream (.-stdout proc)] (.on stream "data" #(.push stdout %)))
      (when-let [stream (.-stderr proc)] (.on stream "data" #(.push stderr %)))
      (wait-for-process proc)
      {:stdout (.join stdout "")
       :stderr (.join stderr "")
       :code (.-exitCode proc)})
    {:stdout (str proc)
     :stderr ""
     :code 0}))

(defn open-io-stream
  "Opens a stream based on operation and target, returns a promise."
  [op target]
  (js/Promise.
    (fn [resolve reject]
      (if (= op :set)
        (resolve target)
        (let [[create-stream flags]
              (case op
                :in [#(.createReadStream fs %1 %2) "r"]
                :out [#(.createWriteStream fs %1 %2) "w"]
                :append [#(.createWriteStream fs %1 %2) "a"]
                :rw [#(.createWriteStream fs %1 %2) "w+"])]
           (.open fs target flags
             (fn [err f]
               (if err
                 (reject err)
                 (resolve (create-stream nil #js{:fd f}))))))))))

(defn open-io-streams
  "Opens io streams based on redirection specification. Returns an array that can be passed as stdio option to spawn."
  [redir]
  (let [arr #js["pipe" "pipe" "pipe"]]
    (when (seq redir)
      (let [result (atom nil)
            p (->> (for [[op fd target] redir]
                     (-> (open-io-stream op target)
                         (.then (fn [stream] [fd stream]))))
                   (apply array)
                   (js/Promise.all))]
           (.then p #(reset! result %))
           (.loopWhile deasync #(not @result))
           (doseq [[fd target] @result]
             (aset arr fd (if (number? target)
                            (aget arr target)
                            (case target
                              :stdin *stdin*
                              :stdout *stdout*
                              :stderr *stderr*
                              target))))))
    arr))

(defn get-command-suggestion
  "Get suggestion for a missing command using command-not-found utility."
  [cmdname]
  (try
    (fs.accessSync command-not-found-bin fs.constants.X_OK)
    (-> (child-process.spawnSync command-not-found-bin #js["--no-failure-msg" cmdname] #js{:encoding "utf-8"})
        (.-stderr)
        (clojure.string/trim))
    (catch :default _)))

(defn handle-spawn-error
  "Formats and prints error from spawn."
  [err]
  (case (.-errno err)
    "ENOENT" (let [cmdname (.-path err)
                   suggestion (get-command-suggestion cmdname)]
               (when-not (clojure.string/blank? suggestion)
                 (js.console.error suggestion))
               (js/console.error (str cmdname ": command not found")))
    (js/console.error "Unexpected error:\n" err)))

(defn shx
  "Executes a command as child process."
  ([cmd] (shx cmd []))
  ([cmd args] (shx cmd args {}))
  ([cmd args opts]
   (doto
     (child-process.spawn
       cmd
       (apply array (flatten args))
       #js{:stdio (open-io-streams (:redir opts))})
     (.on "error" handle-spawn-error))))

; TODO: refactor dispatch
(defn pipe
  "Pipes process or value to another process or function."
  ([from to]
   (cond
     (instance? child-process.ChildProcess from)
     (cond
       (instance? child-process.ChildProcess to)
       (do
         (when-let [stdin (.-stdin to)]
           (.pipe (get-out-stream from) stdin))
         to)

       :else (to (process-output from)))

     (seq? from)
     (cond
       (instance? child-process.ChildProcess to)
       (let [val (str (clojure.string/join "\n" from) "\n")]
         (.write (.-stdin to) val)
         (.end (.-stdin to))
         to)

       :else (to from))

     :else
     (cond
       (instance? child-process.ChildProcess to)
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
              (instance? child-process.ChildProcess x) (line-seq (get-out-stream x))
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

(defn expand-alias
  ([input] (expand-alias *closh-aliases* input))
  ([aliases input]
   (let [token (re-find #"[^\s]+" input)
         alias (get aliases token)]
     (if alias
       (clojure.string/replace-first input #"[^\s]+" alias)
       input))))

(defn expand-abbreviation
  ([input] (expand-alias *closh-abbreviations* input))
  ([aliases input]
   (let [token (re-find #"[^\s]+" input)
         alias (get aliases token)]
     (if (and alias
              (= (clojure.string/trim input) token))
       (clojure.string/replace-first input #"[^\s]+" alias)
       input))))

(defn handle-line
  "Parses given string, evals and waits for execution to finish. Pass in the `eval-cljs` function that evals forms in desired context."
  [input eval-cljs]
  (-> input
    (eval-cljs)
    (wait-for-pipeline)))
