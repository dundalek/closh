(ns closh.builtin
  (:require [clojure.string]
            [goog.object :as gobj]))

(defn cd
  "Changes current working directory to a path of a first given argument."
  [& args]
  ;; flatten is used because we can get arguments from expand which are collections
  (let [dir (or (first (flatten args))
                js/process.env.HOME)]
    (js/process.chdir dir)
    (gobj/set js/process.env "PWD" (js/process.cwd))
    nil))

(defn exit
  "Exits the process using optional first argument as exit code."
  [& args]
  (js/process.exit (first (flatten args))))

(def quit
  "Alias for `exit`."
  exit)
