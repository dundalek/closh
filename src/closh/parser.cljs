(ns closh.parser
  (:require [clojure.string]
            [clojure.set]
            [cljs.spec.alpha :as s]))
            ; #?(:clj [clojure.spec.alpha :as s]
            ;    :cljs [cljs.spec.alpha :as s])))

(def pipes
  {'| 'pipe
   '|> 'pipe-multi
  ;  '|>> ' pipe-thread-last
   ; '|| ' pipe-mapcat
   '|? 'pipe-filter
   '|& 'pipe-reduce})
   ; '|! 'pipe-foreach

(def redirect-op #{'> '< '>> '<< '&> '<> '>&})
(def pipe-op #{'| '|> '|? '|&})
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

(s/def ::redirect (s/cat :fd (s/? number?) :op ::redirect-op :arg ::arg))

(s/def ::arg #(not (op %)))
              ;  (s/or :list list?
              ;        :symbol symbol?
              ;        :string string?
              ;        :number number?))))


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

(declare parse)

(defn process-arg [arg]
  (if (list? arg)
    (if (= (first arg) 'sh)
      (list 'expand-command (parse (rest arg)))
      arg)
    (list (if (string? arg)
            'expand-partial
            'expand)
          (str arg))))

; todo: :redirect
(defn process-command [[cmd & args]]
  (if (and (= (first cmd) :arg) (list? (second cmd)))
    (if (seq args)
      (concat
        (list 'do (second cmd))
        (map second args))
      (second cmd))
    (let [redirects (->> args
                         (filter #(= (first %) :redirect))
                         (map second))
          parameters (->> args
                          (filter #(= (first %) :arg))
                          (map #(process-arg (second %))))]
      (conj
        parameters
        (str (second cmd))
        'shx))))

; TODO: not
(defn process-pipeline [{:keys [not cmd cmds]}]
  (concat
   (list '-> (process-command cmd))
   (for [{:keys [op cmd]} cmds]
     (let [cmd (process-command cmd)
           fn (pipes op)]
        (list fn cmd)))))
  ; [not cmd cmds])
  ; (process-command cmd))

(defn process-command-clause [{:keys [pipeline pipelines]}]
  (process-pipeline pipeline))

(defn process-command-list [{:keys [cmd cmds]}]
  (process-command-clause cmd))

(defn parse [coll]
  (process-command-list (s/conform ::cmd-list coll)))

(process-command-list (s/conform ::cmd-list '(echo (sh date))))

(process-command-list (s/conform ::cmd-list (quote (cat @(sh ls *.txt)))))


; '(echo a | egrep (str "[0-9]+"))
; exit code: 1


; ! echo hi || echo FAILED

;
; (process-command-list (s/conform ::cmd-list '(ls $HOME)))
; (process-command-list (s/conform ::cmd-list '(ls |> (map #(str/replace % #"\.txt" ".md")))))
; (process-command-list (s/conform ::cmd-list '(ls |> (map str/upper-case))))
; (process-command-list (s/conform ::cmd-list '(ls -a | grep "^\\.")))
; (process-command-list (s/conform ::cmd-list '(ls .)))

; (process-command-list (s/conform ::cmd-list '(diff < (sh sort L.txt) < (sh sort R.txt))))
; (process-command-list (s/conform ::cmd-list '(echo x > tmp.txt)))
; (process-command-list (s/conform ::cmd-list '(echo x 2 > tmp.txt)))
; (process-command-list (s/conform ::cmd-list '(echo x >> tmp.txt)))
; (process-command-list (s/conform ::cmd-list '(echo hi 1 >& 2 | wc -l)))

; (process-command-list (s/conform ::cmd-list '(ls | (spit "files.txt"))))
;
; (process-command-list (s/conform ::cmd-list (list 'cat (symbol "a/b/c/d"))))
; (process-command-list (s/conform ::cmd-list (list 'cat (symbol "/a/b/c/d"))))

;
