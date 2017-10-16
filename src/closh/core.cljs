(ns closh.core
  (:require [clojure.string]))

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
  (glob s #js{:nonull true}))

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

(defn get-out-stream [x]
  (if (seq? x)
    (let [s (stream.PassThrough.)]
      (doseq [chunk x]
        (.write s chunk)
        (.write s "\n"))
      (.end s)
      s)
    (if-let [stdout (.-stdout x)]
      stdout
      (let [s (stream.PassThrough.)]
        (.end s)
        s))))

(defn get-data-stream [x]
  (if (seq? x)
    x
    (line-seq (.-stdout x))))

(defn wait-for-process [proc]
  (let [code (atom nil)]
    (.on proc "close" #(reset! code %))
    (.loopWhile deasync #(nil? @code))
    @code))

(defn process-output [proc]
  (let [out #js[]]
    (.on (get-out-stream proc) "data" #(.push out %))
    (wait-for-process proc)
    (.join out "")))

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
                                  :append (.createWriteStream fs target #js{:flags "w+"}))]
                                  ; TODO: :rw])
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
  (f (get-data-stream proc)))

(defn pipe-map [proc f]
  (pipe-multi proc (partial map f)))

(defn pipe-filter [proc f]
  (pipe-multi proc (partial filter f)))
