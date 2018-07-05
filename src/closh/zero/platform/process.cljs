(ns closh.zero.platform.process
  (:require [closh.zero.platform.util :refer [wait-for-event]]))

(def ^:dynamic *stdin* js/process.stdin)
(def ^:dynamic *stdout* js/process.stdout)
(def ^:dynamic *stderr* js/process.stderr)

(def ^:no-doc child-process (js/require "child_process"))

(defn process? [proc]
  (instance? child-process.ChildProcess proc))

(defn wait
  "Wait untils process exits and all of its stdio streams are closed."
  [proc]
  (when (and (process? proc)
             (nil? (.-exitCode proc)))
    (wait-for-event proc "close"))
  proc)

(defn exit [code]
  (js/process.exit code))

(defn cwd []
  (js/process.cwd))

(defn chdir [dir]
  (js/process.chdir dir))
