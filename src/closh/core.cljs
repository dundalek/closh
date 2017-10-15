(ns closh.core
  (:require [clojure.string]))
  ; (:require [cljs.reader :refer [read-string]]))
  ; (:require-macros [closh.core :refer [sh]]))

(def child-process (js/require "child_process"))
(def es (js/require "event-stream"))
(def glob (.-sync (js/require "glob")))
(def deasync (js/require "deasync"))

; (defn read-command [input]
;   (let [s (if (re-find #"^\s*#?\(" input)
;             input
;             (str "(sh " input ")"))]
;     (read-string s)))

;options
; env
; cwd

(defn expand-variable [s]
  (if (re-find #"^\$" s)
    (aget js/process.env (subs s 1))
    s))

(defn expand-tilde [s]
  (clojure.string/replace-first s #"^~" (.-HOME js/process.env)))

(defn expand-filename [s]
  (glob s #js{:nonull true}))

; Bash: Partial quote (allows variable and command expansion)
(defn expand-partial [s]
  (or (expand-variable s) (list)))

; Bash: The order of expansions is: brace expansion; tilde expansion, parameter and variable expansion, arithmetic expansion, and command substitution (done in a left-to-right fashion); word splitting; and filename expansion.
(defn expand [s]
  (if-let [x (expand-variable s)]
    (-> x
      expand-tilde
      expand-filename)
    (list)))

(defn wait-for-process [proc]
  (let [code (atom nil)]
    (.on proc "close" #(reset! code %))
    (.loopWhile deasync #(nil? @code))
    @code))

(defn process-output [proc]
  (let [out #js[]]
    (.on (.-stdout proc) "data" #(.push out %))
    (wait-for-process proc)
    (.join out "")))

(defn expand-command [proc]
  (-> (process-output proc)
      (clojure.string/trim)
      (clojure.string/split  #"\s+")))

(defn shx [cmd & args]
  (child-process.spawn cmd (apply array (flatten args))))

(defn get-out-stream [x]
  (or (.-stdout x)
      (-> x :out (.pipe (.join es "\n")))))

(defn get-data-stream [x]
  (or (:out x)
      (-> x .-stdout (.pipe (.split es "\n")))))

(defn create-data-stream [x]
  {:out x})

(defn pipe [& processes]
  (doseq [[from to] (partition 2 1 processes)]
    (-> from
        get-out-stream
        (.pipe (.-stdin to))))
  (last processes))

(defn pipe-map [proc f]
  (-> proc
      get-data-stream
      (.pipe (.map es (fn [data cb]
                        (cb nil (f data)))))
      create-data-stream))

(defn pipe-filter [proc f]
  (-> proc
      get-data-stream
      (.pipe (.map es (fn [data cb]
                        (if (f data)
                          (cb nil data)
                          (cb)))))
      create-data-stream))
