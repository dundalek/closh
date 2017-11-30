(ns closh.completion
  (:require [clojure.string]))

(def child-process (js/require "child_process"))

(defn stream-output [stream cb]
  (let [out #js[]]
    (doto stream
      (.on "data" #(.push out %))
      (.on "end" #(cb nil (.join out "")))
      (.on "error" #(cb % "")))))

(defn get-completions-spawn [cmd args]
  (js/Promise.
    (fn [resolve reject]
      (let [proc (child-process.spawn cmd args #js{:encoding "utf-8"})]
        (stream-output (.-stdout proc)
          (fn [_ stdout]
            (let [completions (if (clojure.string/blank? stdout)
                                #js[]
                                (apply array (clojure.string/split (clojure.string/trim stdout) #"\n")))]
              (resolve completions))))))))

(defn complete-fish [line]
  (get-completions-spawn "/home/me/github/closh/scripts/shell/completion.fish" #js[line]))

(defn complete-bash [line]
  (get-completions-spawn "/home/me/github/closh/scripts/shell/completion.bash" #js[line]))

(defn complete-zsh [line]
  (get-completions-spawn "/home/me/github/closh/scripts/shell/completion.zsh" #js[line]))

(defn complete [line cb]
  (->
    (complete-fish line)
    (.then #(if (seq %) % (complete-bash line)))
    (.then #(if (seq %) % (complete-zsh line)))
    (.then #(cb nil #js[% line]))
    (.catch #(cb %))))
