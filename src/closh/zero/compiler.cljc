(ns closh.zero.compiler
  (:require [closh.zero.env :refer [*closh-commands*]]))

(def ^:no-doc builtins
  "Set of symbols of builtin functions"
  #{'cd 'exit 'quit 'getenv 'setenv})

(def ^:no-doc pipes
  "Maps shorthand symbols of pipe functions to full name"
  {'| 'pipe
   '|> 'pipe-multi
  ;  '|>> ' pipe-thread-last
   ; '|| ' pipe-mapcat
   '|? 'pipe-filter
   '|& 'pipe-reduce})
   ; '|! 'pipe-foreach

(declare ^:dynamic *process-pipeline*)

(defn ^:no-doc process-arg
  "Transform conformed argument."
  [arg]
  (cond
    ;; clojure form - use as is
    (or (boolean? arg) (number? arg) (seq? arg) (vector? arg)) [arg]
    ;; strings do limited expansion
    (string? arg) (list 'expand-partial arg)
    ;; otherwise coerce to string and do full expansion
    :else (list 'expand (str arg))))

(defn ^:no-doc process-redirect
  "Transform conformed redirection specification."
  [{:keys [op fd arg]}]
  (let [arg (cond
              (list? arg) arg
              (number? arg) arg
              (keyword? arg) arg
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
  ([cmd] (process-command cmd []))
  ([[cmd & rest] redir]
   (let [args (if (vector? (ffirst rest))
                (apply concat rest)
                rest)
         is-function (and (= (first cmd) :arg)
                       (list? (second cmd))
                       (not= 'cmd (first (second cmd))))
         redirects (->> (concat redir args)
                        (filter #(= (first %) :redirect))
                        (mapcat (comp process-redirect second))
                        (vec))
         parameters (->> args
                         (filter #(= (first %) :arg))
                         (map second))]
     (if is-function
       (if (seq parameters)
         (concat
           (list 'do (second cmd))
           parameters)
         (second cmd))
       (let [name (second cmd)
             name-val (if (list? name)
                        (second name) ; when using cmd helper
                        (str name))
             parameters (map process-arg parameters)]
           (cond
             (builtins name)
             `(apply ~name (concat ~@parameters))

             (@*closh-commands* name)
             (if (empty? parameters)
               `((@closh.zero.env/*closh-commands* (quote ~name)))
               `(apply (@closh.zero.env/*closh-commands* (quote ~name)) (concat ~@parameters)))

             :else
             (concat
               (list 'shx name-val)
               [(vec parameters)]
               (if (seq redirects) [{:redir redirects}]))))))))

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
  [{:keys [cmd cmds]} redir-begin redir-end]
  (let [pipeline (butlast cmds)
        end (last cmds)]
    (reduce
     (fn [result [{:keys [op cmd]} redir]]
       (let [rest (rest cmd)
             args (if (vector? (ffirst rest))
                    (apply concat rest)
                    rest)
             redirects (->> (concat redir args)
                            (filter #(= (first %) :redirect))
                            (mapcat (comp process-redirect second))
                            (vec))
             parameters (->> args
                             (filter #(= (first %) :arg))
                             (map second))
             cmd (process-command cmd redir)
             fn (pipes op)
             cmd (if (not (special? (first cmd)))
                   (let [x (gensym)]
                     `(fn [~x]
                        (closh.zero.pipeline/redir ~(concat cmd [x]) ~redirects)))
                   cmd)]
         (list fn result cmd)))
     (process-command cmd redir-begin)
     (concat
      (map (fn [cmd] [cmd []]) pipeline)
      (when end [[end redir-end]])))))

(defn ^:no-doc process-pipeline-interactive
  "Transform conformed pipeline specification in interactive mode. Pipeline by default reads from stdin and writes to stdout."
  ([pipeline]
   (process-pipeline
     pipeline
     (vec (concat [[:redirect {:op '>& :fd 0 :arg :stdin}]
                   [:redirect {:op '>& :fd 2 :arg :stderr}]]
                  (when (empty? (:cmds pipeline)) [[:redirect {:op '>& :fd 1 :arg :stdout}]])))
     [[:redirect {:op '>& :fd 1 :arg :stdout}]
      [:redirect {:op '>& :fd 2 :arg :stderr}]])))

(defn ^:no-doc process-pipeline-batch
  "Transform conformed pipeline specification in batch mode. "
  [pipeline] (process-pipeline pipeline [] []))

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
                   `(let [~tmp (closh.zero.pipeline/wait-for-pipeline ~(*process-pipeline* (:pipeline pipeline)))]
                      (if (~pred (closh.zero.pipeline/pipeline-condition ~tmp))
                        ~child
                        ~tmp)))))
        (-> items
            (first)
            (update :pipeline *process-pipeline*))
        (rest items)))))

;; TODO: handle rest of commands when job control is implemented
(defn ^:no-doc process-command-list
  "Transform conformed command list specification."
  [{:keys [cmd cmds]}]
  (process-command-clause cmd))

(defn compile-interactive
  "Parse tokens in command mode into clojure form that can be evaled. First it runs spec conformer and then does the transformation of conformed result. Uses interactive pipeline mode."
  [ast]
  (binding [*process-pipeline* process-pipeline-interactive]
    (process-command-list ast)))

(defn compile-batch
  "Parse tokens in command mode into clojure form that can be evaled. First it runs spec conformer and then does the transformation of conformed result. Uses batch pipeline mode."
  [ast]
  (binding [*process-pipeline* process-pipeline-batch]
    (process-command-list ast)))
