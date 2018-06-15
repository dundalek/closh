(ns closh.zero.platform.process
  (:require [closh.zero.platform.util :refer [wait-for-event]]))

(def ^:dynamic *stdin* js/process.stdin)
(def ^:dynamic *stdout* js/process.stdout)
(def ^:dynamic *stderr* js/process.stderr)

#?(:cljs
   (do
     (def ^:no-doc child-process (js/require "child_process"))))

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
  #?(:cljs (js/process.exit code)
     :clj (System/exit code)))

; Might not be right be should do for now
; https://stackoverflow.com/questions/1234795/why-is-the-user-dir-system-property-working-in-java
(defn cwd []
  #?(:cljs (js/process.cwd)
     :clj (.getAbsolutePath (File. ""))))

(defn chdir [dir]
  #?(:cljs (js/process.chdir dir)
     :clj (System/setProperty "user.dir" (.getAbsolutePath (File. dir)))))
