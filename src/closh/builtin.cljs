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

(defn jsx->clj
  "Takes a js object and returns a cljs map. Use this when js->clj doesn't work a nonstandard object"
  [x]
  (into {} (for [k (js/Object.keys x)] [k (gobj/get x k)])))

(defn getenv
  "Gets environment variables. Given X args where X is:
  0  - Returns a map of all environment variables and their values
  1  - Returns the value of the specified environment variable as a string
  >1 - Returns a map of the specified variables and their values"
  [& args]
  (let [ks (flatten args)]
    (condp = (count ks)
      0 (jsx->clj js/process.env)
      1 (gobj/get js/process.env (first ks))
      (into {} (map
                #(vector % (gobj/get js/process.env %))
                ks)))))

(defn setenv
  "Sets environment variables. Takes args as key value pairs and returns a list of values"
  [& args]
  (doall (map
          (fn [[k v]] (aset js/process.env k v))
          (partition 2 (flatten args)))))
