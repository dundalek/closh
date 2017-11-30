(ns closh.completion
  (:require [clojure.string]))

(def child-process (js/require "child_process"))

(defn stream-output [stream cb]
  (let [out #js[]]
    (doto stream
      (.on "data" #(.push out %))
      (.on "end" #(cb nil (.join out "")))
      (.on "error" #(cb % "")))))

(defn get-completions-spawn [cmd args cb]
  (let [proc (child-process.spawn cmd args #js{:encoding "utf-8"})]
    (stream-output (.-stdout proc)
      (fn [err stdout]
        (if err
          (cb err)
          (let [completions (if (clojure.string/blank? stdout)
                              #js[]
                              (apply array (clojure.string/split (clojure.string/trim stdout) #"\n")))]
            (cb nil completions)))))))

(defn complete-fish [line cb]
  (get-completions-spawn "fish" #js["/home/me/github/closh/scripts/shell/completion.fish" line] cb))

(defn complete-bash [line cb]
  (get-completions-spawn "bash" #js["/home/me/github/closh/scripts/shell/completion.bash" line] cb))

(defn complete-zsh [line cb]
  (get-completions-spawn "zsh" #js["/home/me/github/closh/scripts/shell/completion.zsh" line] cb))

(defn complete [line cb]
  (complete-fish line
    (fn [err completions]
      (if err
        (cb err)
        (cb nil #js[completions line])))))
