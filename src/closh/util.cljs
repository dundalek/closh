(ns closh.util
  (:require [clojure.data :refer [diff]]))

(def ^:no-doc fs (js/require "fs"))
(def ^:no-doc child-process (js/require "child_process"))
(def ^:no-doc os (js/require "os"))
(def ^:no-doc path (js/require "path"))

(def ignore-env-vars #{"_" "OLDPWD" "PWD" "SHELLOPTS" "SHLVL"})

(defn mktemp
  "Return a temporary file name, given an identifier string"
  [s]
  (path.join (os.tmpdir) (str s (rand-int 65535))))

(defn jsx->clj
  "Takes a js object and returns a cljs map. Use this when js->clj doesn't work a nonstandard object"
  [x]
  (into {} (for [k (js/Object.keys x)] [k (aget x k)])))

(defn spawn-shell
  [shell exp]
  (let [child (child-process.spawnSync shell (clj->js ["-c" exp]))]
    {:status (aget child "status")
     :stdout (str (aget child "stdout"))
     :stderr (str (aget child "stderr"))}))

(defn setenv-diff
  [before after]
  (let [var_diff (diff before after)
        removed (remove #(ignore-env-vars (first %)) (first var_diff))
        changed (remove #(ignore-env-vars (first %)) (second var_diff))]
    (doseq [[k _] removed] (js-delete js/process.env k))
    (doseq [[k v] changed] (aset js/process.env k v))))

;; TODO
;; - [ ] Is it correct to return the contents of stdout?
;; - [ ] How to handle stderr?
;; - [ ] How to handle errors during execution?
;; - [ ] How to return status code?
;; - [ ] It would be nice to use something in memory instead of a temp file, but
;;       beware issues with interactive scripts running in the interpreter
;; - [ ] When closh does aliases, add them here
(defn source
  "Spawns a shell interpreter and executes `exp`. If it executes successfully,
  any exported variables are then saved into the closh environment"
  ([exp] (source "bash" exp))
  ([shell exp]
   (let [before (jsx->clj js/process.env)
         temp-file (mktemp "closh")
         result (spawn-shell shell (str exp "&& (node -p 'JSON.stringify(process.env)') >" temp-file))
         after (js->clj (js/JSON.parse (fs.readFileSync temp-file "utf8")))]
     (when (fs.existsSync temp-file) (fs.unlinkSync temp-file))
     (if (= (:status result) 0)
       (let [stdout (:stdout result)]
         (setenv-diff before after)
         (when-not (= stdout "") stdout))
       (println "Error while executing" shell "command (" exp ")\n" (:stderr result))))))
