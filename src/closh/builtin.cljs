(ns closh.builtin
  (:require [clojure.string]))

(defn cd
  "Changes current working directory to a path of a first given argument"
  [& args]
  ;; flatten is used because we can get arguments from expand which are collections
  (process.chdir (first (flatten args))))

(defn exit
  "Exits the process using optional first argument as exit code"
  [& args]
  (process.exit (first (flatten args))))

(def quit
  "Alias for `exit`"
  exit)
