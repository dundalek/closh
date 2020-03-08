(ns closh.zero.frontend.sci
  (:gen-class)
  (:require
   ; [closh.zero.compiler]
   ; [closh.zero.parser :as parser]
   ; [closh.zero.pipeline]
   ; [closh.zero.platform.eval :as eval]
   ; [closh.zero.platform.process :as process]
   ; [closh.zero.env :as env]
   ; [closh.zero.reader :as reader]
   [closh.zero.utils.clojure-main-sci :refer [main]]))

#_(defn repl-print
    [result]
    (when-not (or (nil? result)
                  (identical? result env/success)
                  (process/process? result))
      (if (or (string? result)
              (char? result))
        (print result)
        (pr result))
      (flush)))

#_(defn -main [& args]
    (reset! process/*cwd* (System/getProperty "user.dir"))
    (let [cmd (or (first args) "echo hello clojure")]
      (repl-print
       (eval/eval
        `(-> ~(closh.zero.compiler/compile-interactive
               (closh.zero.parser/parse
                (reader/read-string cmd)
                #_(edamame/parse-string-all cmd {:all true})))
             (closh.zero.pipeline/wait-for-pipeline))))))

(defn -main [& args]
  (apply main args))
