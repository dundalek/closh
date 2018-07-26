(ns closh.zero.platform.util
  (:require [goog.object :as gobj]))

(def ^:no-doc deasync (js/require "deasync"))

(defn wait-for-event
  "Synchronously wait for an event to be trigerred on an event emitter."
  [emitter event]
  (let [done (atom false)]
    (.on emitter event #(reset! done {:val %}))
    (.loopWhile deasync #(not @done))
    (:val @done)))

(defn jsx->clj
  "Takes a js object and returns a cljs map. Use this when js->clj doesn't work a nonstandard object"
  [x]
  (into {} (for [k (js/Object.keys x)] [k (gobj/get x k)])))
