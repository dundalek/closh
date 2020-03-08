(ns closh.zero.frontend.plain
  (:gen-class)
  (:require [closh.zero.platform.eval :as eval]
            [closh.zero.compiler]
            [closh.zero.parser]
            [closh.zero.pipeline]
            [closh.zero.reader]))

(defn -main [& args]
  (let [cmd (or (first args) "echo hello clojure")]
    (eval/eval
     `(-> ~(closh.zero.compiler/compile-interactive
            (closh.zero.parser/parse (closh.zero.reader/read-string cmd)))
          (closh.zero.pipeline/wait-for-pipeline)))))
