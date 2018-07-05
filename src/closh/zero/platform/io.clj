(ns closh.zero.platform.io
  (:require [me.raynes.conch.low-level :as sh]))

(defn process-output
  "Returns for a process to finish and returns output to be printed out."
  [proc]
  (sh/stream-to-string proc :out))
