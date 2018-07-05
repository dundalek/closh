(ns closh.builtin
  (:require [clojure.string]
            [closh.zero.platform.process :as process]))

(defn exit
  "Exits the process using optional first argument as exit code."
  [code & _]
  (process/exit code))

(def quit
  "Alias for `exit`."
  exit)

(defn getenv
  "Gets environment variables. Given X args where X is:
  0  - Returns a map of all environment variables and their values
  1  - Returns the value of the specified environment variable as a string
  >1 - Returns a map of the specified variables and their values"
  [& args]
  (apply process/getenv args))

(defn setenv
  "Sets environment variables. Takes args as key value pairs and returns a list of values"
  [& args]
  (doall (map
          (fn [[k v]] (process/setenv k v))
          (partition 2 args))))

(defn cd
  "Changes current working directory to a path of a first given argument."
  [dir & _]
  ;; flatten is used because we can get arguments from expand which are collections
  (let [dir (or dir
                (getenv "HOME"))]
    (process/chdir dir)
    (setenv "PWD" (process/cwd))
    nil))
