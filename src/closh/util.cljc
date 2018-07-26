(ns closh.util
  (:require [clojure.data :refer [diff]]
            [closh.zero.platform.process :refer [shx setenv getenv]]
            [closh.zero.pipeline :refer [process-value]]))

#?(:cljs
   (do
     (def ^:no-doc fs (js/require "fs"))
     (def ^:no-doc child-process (js/require "child_process"))
     (def ^:no-doc os (js/require "os"))
     (def ^:no-doc path (js/require "path"))
     (def ^:no-doc tmp (js/require "tmp"))))

(def ignore-env-vars #{"_" "OLDPWD" "PWD" "SHELLOPTS" "SHLVL"})

(defn spawn-shell
  [shell exp]
  (process-value (shx shell ["-c" exp])))

(defn setenv-diff
  [before after]
  (let [var-diff (diff before after)
        removed (remove #(ignore-env-vars (first %)) (first var-diff))
        changed (remove #(ignore-env-vars (first %)) (second var-diff))]
    (doseq [[k _] removed] (setenv k))
    (doseq [[k v] changed] (setenv k v))))

(defn source-shell
  "Spawns a shell interpreter and executes `exp`. If it executes successfully,
  any exported variables are then saved into the closh environment"
  ([exp] (source-shell "bash" exp))
  ([shell exp]
   (let [before (getenv)
         temp-file (tmp.tmpNameSync)
         result (spawn-shell shell (str exp "&& (node -p 'JSON.stringify(process.env)') >" temp-file))]
     (if (= (:code result) 0)
       (let [after (js->clj (js/JSON.parse (fs.readFileSync temp-file "utf8")))
             stdout (:stdout result)]
         (fs.unlinkSync temp-file)
         (setenv-diff before after)
         (when-not (= stdout "") stdout))
       (println "Error while executing" shell "command:" exp "\n" (:stderr result))))))
