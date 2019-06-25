(ns closh.zero.builtin
  (:require [clojure.string]
            [closh.zero.platform.process :as process]
            [closh.zero.env :as env]))

(defn exit
  "Exits the process using optional first argument as exit code."
  ([]
   (exit 0))
  ([code & _]
   (process/exit code)))

(def quit
  "Alias for `exit`."
  exit)

(defn getenv
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

(defn setenv
  "Sets environment variables. Takes args as key value pairs and returns a list of values"
  [& args]
  (doall (map
          (fn [[k v]] (process/setenv k v))
          (partition 2 args))))

(def ^:private dir-stack (atom (list (getenv "PWD"))))

;;; Push and entry on the directory stack
(defn- push-dir
  [dir]
  (swap! dir-stack (comp distinct conj) dir))

(defn dh
  "Show the current directory stack"
  []
  (println
   (clojure.string/join "\n" (map-indexed #(str %1 " " %2) @dir-stack))))

(defn cd
  "Changes current working directory to a path of a first given argument.
  If the first arg is like -<N> it will use the Nth entry on the directory
  stack. A '-' changes directory to the previous directory."
  [& args]
  ;; flatten is used because we can get arguments from expand which are collections
  (let [arg (first args)]
    (if-let [dir (cond (nil? arg)       ;go home if no arg
                       (getenv "HOME")

                       ;; a lone '-' will change to the last directory
                       (= arg "-")
                       (nth @dir-stack 1)

                       ;; a -<N> shows up in the arg list as a negative number
                       (and (integer? arg)
                            (< arg 1))
                       (nth @dir-stack (- arg))

                       ;; just use the arg
                       :else arg)]
      (do (process/chdir dir)
          (let [cwd (process/cwd)]
            ;; save the directory on the directory stack
            (push-dir cwd)
            (setenv "PWD" cwd))
          env/success)
      ;; if we don't have a directory then fail (shouldn't really get
      ;; here).
      env/failure)))
