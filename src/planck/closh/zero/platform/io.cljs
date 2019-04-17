(ns closh.zero.platform.io
  (:require [planck.core]))

(def ^:dynamic *stdin* nil)
(def ^:dynamic *stdout* nil)
(def ^:dynamic *stderr* nil)

(defn glob [s _]
  ;; TODO
  [s])

(defn line-seq
  "Create a lazy seq of strings from stream"
  ([stream]
   (planck.core/line-seq stream)))

(defn out-stream
  "Get stdout stream of a given process."
  [proc]
  (:out proc))

(defn in-stream [proc])
  ;;(.-stdin proc))

(defn err-stream [proc]
  (:err proc))

(defn stream-output [stream]
  (delay stream))

(defn stream-write [stream val]
  #_ (doto stream
       (.write val)
       (.end)))

(defn pipe-stream [from to]
  #_(.pipe from to))

#_ (defn open-io-stream
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

#_ (defn open-io-streams
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
  #_ (instance? stream/Readable s))

(defn output-stream? [s]
  #_ (instance? stream/Writable s))

(defn input-stream [x & opts]
  #_ (fs/createReadStream x))

(defn output-stream [x & opts]
  #_ (let [append? (:append (apply hash-map opts))
           flags (if append? "a" "w")]
       (fs/createWriteStream x #js{:flags flags})))
