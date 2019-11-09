(ns closh.zero.platform.io
  (:require [glob :as glob-js]
            [fs]
            [deasync]
            [stream]))

(def ^:dynamic *stdin* js/process.stdin)
(def ^:dynamic *stdout* js/process.stdout)
(def ^:dynamic *stderr* js/process.stderr)

(defn glob [s _]
  (seq (glob-js/sync s #js{:nonull true})))

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
                   (deasync/loopWhile #(and (not @done) (empty? buf)))
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
     (if (seq line)
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
               :in [#(fs/createReadStream %1 %2) "r"]
               :out [#(fs/createWriteStream %1 %2) "w"]
               :append [#(fs/createWriteStream %1 %2) "a"]
               :rw [#(fs/createWriteStream %1 %2) "w+"])]
         (fs/open target flags
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
        (deasync/loopWhile #(not @result))
        (doseq [[fd target] @result]
          (aset arr fd (if (number? target)
                         (aget arr target)
                         (case target
                           :stdin *stdin*
                           :stdout *stdout*
                           :stderr *stderr*
                           target))))))
    arr))

(defn input-stream? [s]
  (instance? stream/Readable s))

(defn output-stream? [s]
  (instance? stream/Writable s))

(defn input-stream [x & _]
  (fs/createReadStream x))

(defn output-stream [x & opts]
  (let [append? (:append (apply hash-map opts))
        flags (if append? "a" "w")]
    (fs/createWriteStream x #js{:flags flags})))
