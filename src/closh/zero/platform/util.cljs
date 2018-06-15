(ns closh.zero.platform.util)

(def ^:no-doc deasync (js/require "deasync"))

(defn wait-for-event
  "Synchronously wait for an event to be trigerred on an event emitter."
  [emitter event]
  (let [done (atom false)]
    (.on emitter event #(reset! done {:val %}))
    (.loopWhile deasync #(not @done))
    (:val @done)))
