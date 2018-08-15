(ns closh.zero.service.completion
  (:require [clojure.string]
            #?(:cljs [lumo.repl])
            [closh.builtin :refer [getenv]]
            [closh.zero.platform.io :refer [out-stream]]
            [closh.zero.platform.process :refer [shx]]
            [closh.macros #?(:clj :refer :cljs :refer-macros) [chain->]]))

#?(:cljs
   (defn- stream-output
     "Helper function to get output from a node stream as a string."
     [stream]
     (js/Promise.
       (fn [resolve reject]
        (let [out (closh.zero.platform.io/stream-output stream)]
          (doto stream
            (.on "end" #(resolve @out))
            (.on "error" #(resolve ""))))))))

(defn get-completions-spawn
  "Get completions by spawning a command."
  [cmd args]
  (let [proc (shx cmd args)
        stream (out-stream proc)]
    (chain->
      #?(:cljs (stream-output stream)
         :clj @(closh.zero.platform.io/stream-output stream))
      (fn [stdout]
        (if (clojure.string/blank? stdout)
          []
          (clojure.string/split (clojure.string/trim stdout) #"\n"))))))

(defn complete-fish
  "Get completions from a fish shell. Spawns a process."
  [line]
  (chain-> (get-completions-spawn (str (getenv "CLOSH_SOURCES_PATH") "/scripts/completion/completion.fish") [line])
           (fn [completions] (map #(first (clojure.string/split % #"\t")) completions)))) ; discard the tab-separated description

(defn complete-bash
  "Get completions from bash. Spawns a process."
  [line]
  (get-completions-spawn (str (getenv "CLOSH_SOURCES_PATH") "/scripts/completion/completion.bash") [line]))

(defn complete-zsh
  "Get completions from zsh. Spawns a process."
  [line]
  (get-completions-spawn (str (getenv "CLOSH_SOURCES_PATH") "/scripts/completion/completion.zsh") [line]))

#?(:cljs
   (defn complete-lumo
     "Get completions from Lumo."
     [line]
     (js/Promise.
       (fn [resolve reject]
         (try
           (lumo.repl/get-completions line resolve)
           (catch :default e (reject e)))))))

(defn append-completion
  "Appends completion to a line, discards the common part from in between."
  [line completion]
  (let [line-lower (clojure.string/lower-case line)
        completion-lower (clojure.string/lower-case completion)]
    (loop [i (count completion-lower)]
      (if (zero? i)
        (str line completion)
        (let [sub (subs completion-lower 0 i)]
          (if (clojure.string/ends-with? line-lower sub)
            (str (subs line 0 (- (count line) i)) completion)
            (recur (dec i))))))))

(defn process-completions
  "Processes completions for a given line, cleans up results by removing duplicates."
  [line completions]
  (->> completions
    (map #(append-completion line %))
    (filter #(not= line %))
    (distinct))) ; bash seems to return duplicates sometimes

#?(:cljs
   (defn complete
     "Gets completions for a given line. Delegates to existing shells and Lumo. Callback style compatible with node's builtin readline completer function."
     [line cb]
     (->
       (chain-> (js/Promise.all
                 #js[(when (re-find #"\([^)]*$" line) ; only send exprs with unmatched paren to lumo
                       (complete-lumo line))
                     (chain-> (complete-fish line)
                       #(if (seq %) % (complete-bash line))
                       #(if (seq %) % (complete-zsh line)))])
         (fn [completions]
           (->> completions
             (map #(process-completions line %))
             (interpose [""])
             (apply concat)
             (apply array)))
         #(cb nil #js[% line]))
       (.catch #(cb %)))))
