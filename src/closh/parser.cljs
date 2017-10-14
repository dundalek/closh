(ns closh.parser
  (:require [clojure.string]
            [clojure.set]
            [cljs.spec.alpha :as s]))
            ; #?(:clj [clojure.spec.alpha :as s]
            ;    :cljs [cljs.spec.alpha :as s])))

(def redirect-op #{'> '< '>> '<< '&> '<>})
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

(def pipes
  {'|> ' pipe-thread-first
   '|>> ' pipe-thread-last
   ; '|| ' pipe-mapcat
   '|? 'pipe-filter
   '|& 'pipe-reduce})
   ; '|! 'pipe-foreach

(def pipe-set (conj (set (keys pipes)) '|))

(defn- command [cmd & args]
  (conj
   (for [arg args]
      (cond
        (symbol? arg) (list 'expand (str arg))
        :else arg))
   (str cmd)
   'shx))

(defn- handle-pipes [[x & xs]]
  (concat
    (list '-> x)
    (for [[op cmd] (partition 2 xs)]
      (let [fn (cond
                 (= op '|) (if (= (first cmd) 'shx) 'pipe 'pipe-map)
                 :else (pipes op))]
        (list fn cmd)))))


; (macroexpand '(command git commit -a (+ 1 2)))
;
; (macroexpand '(sh git commit -a |>> #(cat %) | head -n 10))

; (defmacro sh [& tokens]
;   (->> tokens
;        (partition-by pipe-set)
;        (map (fn [s
;                  (cond)]
;                 (pipe-set (first s)) (first s)
;                 ; for now just get the first function
;                 (list? (first s)) (if (= (count (first s)) 1)
;                                     (ffirst s)
;                                     (first s))
;                 ; (list? (first s)) s
;                 :else (apply command s)))
;        (handle-pipes)))
; ;

(defn process-command [tokens]
  (conj
    (map #(-> % second str) tokens)
    'shx))

; TODO: not
(defn process-pipeline [{:keys [not cmd cmds]}]
  (process-command cmd))

(defn process-command-clause [{:keys [pipeline pipelines]}]
  (process-pipeline pipeline))

(defn process-command-list [{:keys [cmd cmds]}]
  (process-command-clause cmd))

; (def tokens (s/conform ::cmd-list '(ls -l)))
;
; (process-command-list tokens)
