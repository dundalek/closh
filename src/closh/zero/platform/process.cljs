(ns closh.zero.platform.process
  (:require [closh.zero.platform.util :refer [wait-for-event]]
            [closh.zero.platform.io :refer [open-io-streams]]
            [goog.object :as gobj]
            [closh.util :refer [jsx->clj]]))

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

(defn shx
  "Executes a command as child process."
  ([cmd] (shx cmd []))
  ([cmd args] (shx cmd args {}))
  ([cmd args opts]
   (child-process.spawn
     cmd
     (apply array (flatten args))
     #js{:stdio (open-io-streams (:redir opts))})))

(defn setenv [k v]
  (aset js/process.env k v))

(defn getenv
  ([] (jsx->clj js/process.env))
  ([k] (gobj/get js/process.env k)))
