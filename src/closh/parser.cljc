(ns closh.parser
  (:require [clojure.string]
            [clojure.set]
            [clojure.spec.alpha :as s]))

(def ^:no-doc pipes
  "Maps shorthand symbols of pipe functions to full name"
  {'| 'pipe
   '|> 'pipe-multi
  ;  '|>> ' pipe-thread-last
   ; '|| ' pipe-mapcat
   '|? 'pipe-filter
   '|& 'pipe-reduce})
   ; '|! 'pipe-foreach

(def ^:no-doc builtins
  "Set of symbols of builtin functions"
  #{'cd 'exit 'quit})

(def ^:no-doc redirect-op
  "Set of symbols of redirection operators"
   #{'> '< '>> '&> '&>> '<> '>&})

(def ^:no-doc pipe-op
  "Set of symbols of pipe operators"
   #{'| '|> '|? '|&})

(def ^:no-doc clause-op
  "Set of symbols of command clause operators (conditional execution with `&&` and `||`)"
   #{'|| '&&})

(def ^:no-doc cmd-op
  "Set of symbols of operators separating commands"
  #{'&})

(def ^:no-doc op
  "Set of symbols of all operators"
  (clojure.set/union redirect-op pipe-op clause-op cmd-op))

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

(declare parse)

(defn ^:no-doc process-arg
  "Transform conformed argument."
  [arg]
  (cond
    ;; clojure form - use as is
    (list? arg) arg
    ;; strings do limited expansion
    (string? arg) (list 'expand-partial arg)
    ;; otherwise do full expansion
    :else (list 'expand (str arg))))

(defn ^:no-doc process-redirect
  "Transform conformed redirection specification."
  [{:keys [op fd arg]}]
  (let [arg (cond
              (list? arg) arg
              (number? arg) arg
              :else (list 'expand-redirect (str arg)))]
    (case op
      > [[:out (or fd 1) arg]]
      < [[:in (or fd 0) arg]]
      >> [[:append (or fd 1) arg]]
      &> [[:out 1 arg]
          [:set 2 1]]
      &>> [[:append 1 arg]
           [:set 2 1]]
      <> [[:rw (or fd 0) arg]]
      >& [[:set (or fd 1) arg]])))

(defn ^:no-doc process-command
  "Transform conformed command specification."
  [[cmd & args]]
  (if (and (= (first cmd) :arg)
           (list? (second cmd))
           (not= 'cmd (first (second cmd))))
    (if (seq args)
      (concat
        (list 'do (second cmd))
        (map second args))
      (second cmd))
    (let [name (second cmd)
          name-val (if (list? name)
                     (second name) ; when using cmd helper
                     (str name))
          redirects (->> args
                         (mapcat #(if (vector? (first %)) % [%]))
                         (filter #(= (first %) :redirect))
                         (mapcat (comp process-redirect second))
                         (into []))
          parameters (->> args
                          (filter #(= (first %) :arg))
                          (map #(process-arg (second %))))]
        (if (builtins name)
          (conj parameters name)
          (concat
            (list 'shx name-val)
            [(vec parameters)]
            (if (seq redirects) [{:redir redirects}]))))))

(defn ^:no-doc special?
  "Predicate to detect special form so we know not to partial apply it when piping.
  CLJS does not support dynamic macro detection so we also list selected macros."
  [symb]
  (or
   (special-symbol? symb)
   (#{'shx 'fn} symb)))
   ; TODO: how to dynamically resolve and check for macro?
   ; (-> symb resolve meta :macro boolean)))

(defn ^:no-doc process-pipeline
  "Transform conformed pipeline specification."
  [{:keys [cmd cmds]}]
  (concat
   (list '-> (process-command cmd))
   (for [{:keys [op cmd]} cmds]
     (let [cmd (process-command cmd)
           fn (pipes op)]
       (cond
         (and (= op '|>) (not (special? (first cmd)))) (list fn (conj cmd 'partial))
         (and (= op '|) (not (special? (first cmd)))) (list fn (conj cmd 'partial))
         :else (list fn cmd))))))

(defn ^:no-doc process-command-clause
  "Transform conformed command clause specification, handle conditional execution."
  [{:keys [pipeline pipelines]}]
  (let [items (reverse (conj (seq pipelines) {:pipeline pipeline}))]
    (:pipeline
      (reduce
        (fn [{op :op child :pipeline} pipeline]
          (let [condition (if (= op '&&) true false)
                neg (if (:not (:pipeline pipeline)) (not condition) condition)
                pred (if neg 'true? 'false?)
                tmp (gensym)]
            (assoc pipeline :pipeline
                   `(let [~tmp (closh.core/wait-for-pipeline ~(process-pipeline (:pipeline pipeline)))]
                      (if (~pred (closh.core/pipeline-condition ~tmp))
                        ~child
                        ~tmp)))))
        (-> items
            (first)
            (update :pipeline process-pipeline))
        (rest items)))))

;; TODO: handle rest of commands when job control is implemented
(defn ^:no-doc process-command-list
  "Transform conformed command list specification."
  [{:keys [cmd cmds]}]
  (process-command-clause cmd))

(defn parse
  "Parse tokens in command mode into clojure form that can be evaled. First it runs spec conformer and then does the transformation of conformed result."
  [coll]
  (process-command-list (s/conform ::cmd-list coll)))
