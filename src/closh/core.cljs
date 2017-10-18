(ns closh.core
  (:require [clojure.string]
            [clojure.tools.reader]
            [closh.parser :refer [parse]]))

(def fs (js/require "fs"))
(def child-process (js/require "child_process"))
(def stream (js/require "stream"))
(def glob (.-sync (js/require "glob")))
(def deasync (js/require "deasync"))


(defn expand-variable [s]
  (if (re-find #"^\$" s)
    (aget js/process.env (subs s 1))
    s))

(defn expand-tilde [s]
  (clojure.string/replace-first s #"^~" (.-HOME js/process.env)))

(defn expand-filename [s]
  (seq (glob s #js{:nonull true})))

; Expand for redirect targets
(defn expand-redirect [s]
  (-> s
      (expand-tilde)
      (expand-variable)))

; Bash: Partial quote (allows variable and command expansion)
(defn expand-partial [s]
  (or (expand-variable s) (list)))

; Bash: The order of expansions is: brace expansion; tilde expansion, parameter and variable expansion, arithmetic expansion, and command substitution (done in a left-to-right fashion); word splitting; and filename expansion.
(defn expand [s]
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

    (seq? x)
    (let [s (stream.PassThrough.)]
      (doseq [chunk x]
        (.write s chunk)
        (.write s "\n"))
      (.end s)
      s)

    :else
    (let [s (stream.PassThrough.)]
      (.write s (pr-str x))
      (.end s)
      s)))

(defn wait-for-event [proc event]
  (let [done (atom false)]
    (.on proc event #(reset! done {:val %}))
    (.loopWhile deasync #(not @done))
    (:val @done)))

(defn wait-for-process [proc]
  (wait-for-event proc "close"))

(defn process-output [proc]
  (let [out #js[]]
    (.on (get-out-stream proc) "data" #(.push out %))
    (wait-for-process proc)
    (.join out "")))

(defn stream-output [stream]
  (let [out #js[]]
    (.on (get-out-stream stream) "data" #(.push out %))
    (wait-for-event stream "finish")
    (.join out "")))

(defn get-data-stream [x]
  (if (instance? child-process.ChildProcess x)
    (stream-output (get-out-stream x))
    x))

(defn get-seq-stream [x]
  (cond
    (instance? child-process.ChildProcess x) (line-seq (.-stdout x))
    (seq? x) x
    :else (seq x)))

(defn expand-command [proc]
  (-> (process-output proc)
      (clojure.string/trim)
      (clojure.string/split  #"\s+")))

(defn get-streams [redir]
  (let [result (atom nil)
        p (->> (for [[op fd target] redir]
                 (if (= op :set)
                   (js/Promise.resolve [op fd target])
                   (let [stream (case op
                                  :in (.createReadStream fs target)
                                  :out (.createWriteStream fs target)
                                  :append (.createWriteStream fs target #js{:flags "a"})
                                  :rw (.createWriteStream fs target #js{:flags "w+"}))]
                     (js/Promise.
                       (fn [resolve reject]
                         (.on stream "open" #(resolve [op fd stream])))))))
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

(defn shx
  ([cmd] (shx cmd []))
  ([cmd args] (shx cmd args {}))
  ([cmd args opts]
   (child-process.spawn
     cmd
     (apply array (flatten args))
     (build-options opts))))

(defn pipe
  ([from to]
   (if (fn? to)
     (-> from
         get-data-stream
         to)
     (do (-> from
             get-out-stream
             (.pipe (.-stdin to)))
         to)))
  ([x & xs]
   (reduce pipe x xs)))

(defn pipe-multi [proc f]
  (f (get-seq-stream proc)))

(defn pipe-map [proc f]
  (pipe-multi proc (partial map f)))

(defn pipe-filter [proc f]
  (pipe-multi proc (partial filter f)))


(defn handle-code [input eval-cljs]
  (->> input
    (clojure.tools.reader/read-string)
    (eval-cljs)
    (prn-str)
    (.write js/process.stdout)))

(defn handle-command [input eval-cljs]
  (let [proc (-> (str "(" input ")")
               (clojure.tools.reader/read-string)
               (parse)
               (eval-cljs))
        stdout (get-out-stream proc)]
    (when-let [stderr (.-stderr proc)]
      (.pipe stderr js/process.stdout))
    (.pipe stdout js/process.stdout)
    (cond
      (instance? child-process.ChildProcess proc) (wait-for-process proc)
      :else (wait-for-event stdout "finish"))))

(defn handle-line [input eval-cljs]
  (if (re-find #"^\s*#?\(" input)
    (handle-code input eval-cljs)
    (handle-command input eval-cljs)))
