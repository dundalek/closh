(ns closh.core
  (:require [clojure.string]
            [clojure.set]
            [cljs.reader :refer [read-string]]
            [clojure.spec.alpha :as s])
  (:require-macros [closh.core :refer [sh]]))

(def spawn (.-spawn (js/require "child_process")))
(def es (js/require "event-stream"))

(defn read-command [input]
  (let [s (if (re-find #"^\s*#?\(" input)
            input
            (str "(sh " input ")"))]
    (read-string s)))

;options
; env
; cwd

(defn expand [s]
  s)

(defn shx [cmd & args]
  (spawn cmd (apply array args)))

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

; (s/conform ::cmd-list '(ls -l))
; (s/conform ::cmd-list '((+ 1 2)))
; (s/conform ::cmd-list '(ls *.txt))
; (s/conform ::cmd-list '(ls $HOME))
; (s/conform ::cmd-list '(ls |> (reverse) | head))
; (s/conform ::cmd-list '(ls | #(str/replace % #"\.txt" ".md")))
; (s/conform ::cmd-list '(ls | (str/upper-case)))
; (s/conform ::cmd-list '(ls -a | grep "^\\."))
; (s/conform ::cmd-list '(diff <(sh sort L.txt) <(sh sort R.txt)))
; (s/conform ::cmd-list '(echo x > tmp.txt))
; (s/conform ::cmd-list '(ls .))
; (s/conform ::cmd-list '(ls |> (spit "files.txt")))
;
; (s/conform ::cmd-list '(echo (sh date)))
;
; ; todo symbols with slashes like paths
; (s/conform ::cmd-list '(cat a/b/c/d))
; (s/conform ::cmd-list '(cat /a/b/c/d))
;
; ; todo Invalid number handling:
; (s/conform ::cmd-list '(echo x 2> tmp.txt))
; (s/conform ::cmd-list '(echo hi 1>&2))

; (s/conform ::cmd-list '())



(def redirect-op #{'> '< '>> '<< '&>})
(def pipe-op #{'| '|> '|>> '|? '|&})
(def clause-op #{'|| '&&})
(def cmd-op #{'&}) ; semicolon alternative for separator?

(def op (clojure.set/union redirect-op pipe-op clause-op cmd-op))

(s/def ::redirect-op redirect-op)
(s/def ::pipe-op pipe-op)
(s/def ::clause-op clause-op)
(s/def ::cmd-op cmd-op)


(s/def ::cmd-list (s/cat :cmd ::cmd-clause
                         :cmds (s/* (s/cat :op ::cmd-op
                                           :cmd ::cmd))))

(s/def ::cmd-clause (s/cat :pipeline ::pipeline
                           :pipelines (s/* (s/cat :op ::clause-op
                                                  :pipeline ::pipeline))))

(s/def ::pipeline (s/cat :not (s/? #{'!})
                         :cmd ::cmd
                         :cmds (s/* (s/cat :op ::pipe-op
                                           :cmd ::cmd))))

(s/def ::cmd (s/+ (s/alt :redirect ::redirect
                         :arg ::arg)))

(s/def ::redirect (s/cat :op ::redirect-op :arg ::arg))

(s/def ::arg #(not (op %)))
              ;  (s/or :list list?
              ;        :symbol symbol?
              ;        :string string?
              ;        :number number?))))
