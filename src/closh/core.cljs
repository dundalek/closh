(ns closh.core
  (:require [clojure.string]))

(def ^:no-doc fs (js/require "fs"))
(def ^:no-doc child-process (js/require "child_process"))
(def ^:no-doc stream (js/require "stream"))
(def ^:no-doc glob (.-sync (js/require "glob")))
(def ^:no-doc deasync (js/require "deasync"))

(defn expand-variable
  "Expands env variable, it does not look inside string."
  [s]
  (if (re-find #"^\$" s)
    (aget js/process.env (subs s 1))
    s))

(defn expand-tilde
  "Expands tilde character to a path to user's home directory"
  [s]
  (clojure.string/replace-first s #"^~" (.-HOME js/process.env)))

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
  (or (expand-variable s) (list)))

; Bash: The order of expansions is: brace expansion; tilde expansion, parameter and variable expansion, arithmetic expansion, and command substitution (done in a left-to-right fashion); word splitting; and filename expansion.
(defn expand
  "Expands command-line parameter.

  The order of expansions is variable expansion, tilde expansion and filename expansin."
  [s]
  (if-let [x (expand-variable s)]
    (-> x
      expand-tilde
      expand-filename)
    (list)))

(defn line-seq
  ([stream]
   (let [buf #js[]
         done (atom false)]
      (doto stream
        (.on "end" #(reset! done true))
        (.on "data" #(.push buf %)))
      (line-seq (fn []
                  (when (not @done)
                    (.loopWhile deasync #(or (not @done)
                                             (zero? (.-length buf))))
                    (.shift buf)))
        nil)))
  ([read-chunk line]
   (if-let [chunk (read-chunk)]
     (if (re-find #"\n" (str line chunk))
       (let [lines (clojure.string/split (str line chunk) #"\n")]
         (if (= 1 (count lines))
           (lazy-cat lines (line-seq read-chunk nil))
           (lazy-cat (butlast lines) (line-seq read-chunk (last lines)))))
       (recur read-chunk (str line chunk)))
     (if line
       (list line)
       (list)))))

(defn get-out-stream [x]
  (cond
    (instance? child-process.ChildProcess x)
    (if-let [stdout (.-stdout x)]
      stdout
      (let [s (stream.PassThrough.)]
        (.end s)
        s))

    (sequential? x)
    (let [s (stream.PassThrough.)]
      (doseq [chunk x]
        (.write s chunk)
        (.write s "\n"))
      (.end s)
      s)

    :else
    (let [s (stream.PassThrough.)]
      (.write s (str x))
      (.end s)
      s)))

(defn wait-for-event [proc event]
  (let [done (atom false)]
    (.on proc event #(reset! done {:val %}))
    (.loopWhile deasync #(not @done))
    (:val @done)))

(defn wait-for-process [proc]
  (when (nil? (.-exitCode proc))
    (wait-for-event proc "close"))
  proc)

(defn wait-for-pipeline [proc]
  (if (instance? child-process.ChildProcess proc)
    (let [stdout (get-out-stream proc)]
      (when-let [stderr (and proc (.-stderr proc))]
        (.pipe stderr js/process.stderr))
      (.pipe stdout js/process.stdout)
      (wait-for-process proc))
    proc))

(defn pipeline-condition [proc]
  (if (instance? child-process.ChildProcess proc)
    (zero? (.-exitCode proc))
    (boolean proc)))

(defn process-output [proc]
  (cond
    (instance? child-process.ChildProcess proc)
    (let [out #js[]]
      (.on (get-out-stream proc) "data" #(.push out %))
      (wait-for-process proc)
      (.join out ""))

    (seq? proc) (str (clojure.string/join "\n" proc) "\n")
    :else proc))

(defn stream-output [stream]
  (let [out #js[]]
    (.on stream "data" #(.push out %))
    (wait-for-event stream "finish")
    (.join out "")))

(defn pipeline-value [proc]
  (cond
    (instance? child-process.ChildProcess proc)
    (stream-output (get-out-stream proc))

    :else proc))

(defn get-streams [redir]
  (let [result (atom nil)
        p (->> (for [[op fd target] redir]
                 (if (= op :set)
                   (js/Promise.resolve [op fd target])
                   (let [stream (case op
                                  :in (.createReadStream fs nil #js{:fd (.openSync fs target "r")})
                                  :out (.createWriteStream fs nil #js{:fd (.openSync fs target "w")})
                                  :append (.createWriteStream fs nil #js{:fd (.openSync fs target "a")})
                                  :rw (.createWriteStream fs nil #js{:fd (.openSync fs target "w+")}))]
                      (js/Promise.resolve [op fd stream]))))
                    ;  (js/Promise.
                    ;    (fn [resolve reject]
                    ;      (.on stream "open" #(resolve [op fd stream])))))))
               (apply array)
               (js/Promise.all))]
      (.then p #(reset! result %))
      (.loopWhile deasync #(not @result))
      (let [arr #js["pipe" "pipe" "pipe"]]
        (doseq [[_ fd target] @result]
          (aset arr fd (if (number? target)
                         (aget arr target)
                         target)))
        arr)))

(defn build-options [{:keys [redir]}]
  (if redir
    #js{:stdio (get-streams redir)}
    #js{}))

(defn handle-spawn-error [err]
  (case (.-errno err)
    "ENOENT" (js/console.error (str (.-path err) ": command not found"))
    (js/console.error "Unexpected error:\n" err)))

(defn shx
  ([cmd] (shx cmd []))
  ([cmd args] (shx cmd args {}))
  ([cmd args opts]
   (doto
     (child-process.spawn
       cmd
       (apply array (flatten args))
       (build-options opts))
     (.on "error" handle-spawn-error))))

(defn pipe
  ([from to]
   (cond
     (instance? child-process.ChildProcess from)
     (cond
       (instance? child-process.ChildProcess to)
       (do
         (.pipe (get-out-stream from) (.-stdin to))
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
         (.write (.-stdin to) (str from))
         (.end (.-stdin to))
         to)

       :else (to from))))
  ([x & xs]
   (reduce pipe x xs)))

(defn pipe-multi [x f]
  (let [val (cond
              (instance? child-process.ChildProcess x) (line-seq (get-out-stream x))
              (sequential? x) x
              (string? x) (clojure.string/split x #"\n")
              :else (list x))]
    (pipe val f)))

(defn pipe-map [proc f]
  (pipe-multi proc (partial map f)))

(defn pipe-filter [proc f]
  (pipe-multi proc (partial filter f)))


(defn handle-code [input eval-cljs]
  (->> input
    (eval-cljs)
    (prn-str)
    (.write js/process.stdout)))

(defn handle-command [input eval-cljs]
  (let [proc (-> (str "(sh " input ")")
               (eval-cljs))]
    (wait-for-pipeline proc)))

(defn handle-line [input eval-cljs]
  ; (if (re-find #"^\s*#?\(" input)
  ;   (handle-code input eval-cljs)
    (handle-command input eval-cljs))
