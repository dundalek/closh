(ns closh.zero.platform.io)

(def ^:dynamic *stdin* js/process.stdin)
(def ^:dynamic *stdout* js/process.stdout)
(def ^:dynamic *stderr* js/process.stderr)

(def ^:no-doc glob-js (.-sync (js/require "glob")))
(def ^:no-doc stream (js/require "stream"))
(def ^:no-doc fs (js/require "fs"))
(def ^:no-doc deasync (js/require "deasync"))

(defn glob [s]
  (seq (glob-js s #js{:nonull true})))

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

(defn out-stream
  "Get stdout stream of a given process."
  [proc]
  (.-stdout proc))

(defn in-stream [proc]
  (.-stdin proc))

(defn err-stream [proc]
  (.-stderr proc))

(defn stream-output [stream]
  (if stream
    (let [out #js[]]
      (.on stream "data" #(.push out %))
      (delay (.join out "")))
    (delay "")))

(defn stream-write [stream val]
  (doto stream
    (.write val)
    (.end)))

(defn pipe-stream [from to]
  (.pipe from to))

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
