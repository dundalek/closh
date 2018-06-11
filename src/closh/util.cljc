(ns closh.util
  (:require [clojure.data :refer [diff]]
            [goog.object :as gobj]))

(def ^:no-doc fs (js/require "fs"))
(def ^:no-doc child-process (js/require "child_process"))
(def ^:no-doc os (js/require "os"))
(def ^:no-doc path (js/require "path"))
(def ^:no-doc tmp (js/require "tmp"))

(def ignore-env-vars #{"_" "OLDPWD" "PWD" "SHELLOPTS" "SHLVL"})

(defn jsx->clj
  "Takes a js object and returns a cljs map. Use this when js->clj doesn't work a nonstandard object"
  [x]
  (into {} (for [k (js/Object.keys x)] [k (gobj/get x k)])))

(defn spawn-shell
  [shell exp]
  (let [child (child-process.spawnSync shell #js["-c", exp])]
    {:status (gobj/get child "status")
     :stdout (str (gobj/get child "stdout"))
     :stderr (str (gobj/get child "stderr"))}))

(defn setenv-diff
  [before after]
  (let [var_diff (diff before after)
        removed (remove #(ignore-env-vars (first %)) (first var_diff))
        changed (remove #(ignore-env-vars (first %)) (second var_diff))]
    (doseq [[k _] removed] (js-delete js/process.env k))
    (doseq [[k v] changed] (gobj/set js/process.env k v))))

(defn source-shell
  "Spawns a shell interpreter and executes `exp`. If it executes successfully,
  any exported variables are then saved into the closh environment"
  ([exp] (source-shell "bash" exp))
  ([shell exp]
   (let [before (jsx->clj js/process.env)
         temp-file (tmp.tmpNameSync)
         result (spawn-shell shell (str exp "&& (node -p 'JSON.stringify(process.env)') >" temp-file))]
     (if (= (:status result) 0)
       (let [after (js->clj (js/JSON.parse (fs.readFileSync temp-file "utf8")))
             stdout (:stdout result)]
         (fs.unlinkSync temp-file)
         (setenv-diff before after)
         (when-not (= stdout "") stdout))
       (println "Error while executing" shell "command:" exp "\n" (:stderr result))))))
