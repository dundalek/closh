(ns closh.zero.builtin
  (:require [clojure.string]
            [closh.zero.platform.process :as process]
            [closh.zero.env :as env]
            [closh.zero.macros #?(:clj :refer :cljs :refer-macros) [defcmd]]))

(defcmd exit
  "Exits the process using optional first argument as exit code."
  ([]
   (exit 0))
  ([code & _]
   (process/exit code)))

(def quit
  "Alias for `exit`."
  exit)
(defcmd quit
  exit)

(defcmd getenv
  "Gets environment variables. Given X args where X is:
  0  - Returns a map of all environment variables and their values
  1  - Returns the value of the specified environment variable as a string
  >1 - Returns a map of the specified variables and their values"
  [& args]
  (if (< (count args) 2)
    (apply process/getenv args)
    (into {} (map
               #(vector % (process/getenv %))
               args))))

(defcmd setenv
  "Sets environment variables. Takes args as key value pairs and returns a list of values"
  [& args]
  (doall (map
          (fn [[k v]] (process/setenv k v))
          (partition 2 args))))

(defcmd unsetenv
  "Unsets environment variables."
  [& args]
  (doall (map process/unsetenv args)))

(defcmd cd
  "Changes current working directory to a path of a first given argument."
  [& args]
  ;; flatten is used because we can get arguments from expand which are collections
  (let [dir (or (first args)
                (getenv "HOME"))]
    (process/chdir (str dir)) ; Extra (str ..) to handle case when directory name is a number
    (setenv "PWD" (process/cwd))
    env/success))
