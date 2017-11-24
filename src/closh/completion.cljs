(ns closh.completion
  (:require [clojure.string]))

(def child-process (js/require "child_process"))

(defn complete [line cb]
  (let [result (child-process.spawnSync "fish"
                                        #js["/home/me/github/closh/scripts/shell/completion.fish" line]
                                        #js{:encoding "utf-8"})
        stdout (.-stdout result)
        completions (if (clojure.string/blank? stdout)
                      #js[]
                      (apply array (clojure.string/split (clojure.string/trim stdout) #"\n")))]
    (cb nil #js[completions line])))
