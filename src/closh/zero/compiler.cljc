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
                       (not= 'cmd (first (second cmd))))]
     (if is-function
       (if (seq args)
         (concat
           (list 'do (second cmd))
           (map second args))
         (second cmd))
       (let [name (second cmd)
             name-val (if (list? name)
                        (second name) ; when using cmd helper
                        (str name))
             redirects (->> (concat redir args)
                            (filter #(= (first %) :redirect))
                            (mapcat (comp process-redirect second))
                            (vec))
             parameters (->> args
                             (filter #(= (first %) :arg))
                             (map (comp process-arg second)))]
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

(defn ^:no-doc process-pipeline-command
  "Processes single command within a pipeline."
  ([cmd] (process-pipeline-command cmd []))
  ([{:keys [op cmd]} redir]
   (let [cmd (process-command cmd redir)
         fn (pipes op)]
     (cond
       (and (= op '|>) (not (special? (first cmd)))) (list fn (conj cmd 'partial))
       (and (= op '|) (not (special? (first cmd)))) (list fn (conj cmd 'partial))
       :else (list fn cmd)))))

(defn ^:no-doc process-pipeline-interactive
  "Transform conformed pipeline specification in interactive mode. Pipeline by default reads from stdin and writes to stdout."
  [{:keys [cmd cmds]}]
  (let [pipeline (butlast cmds)
        end (last cmds)
        redir-begin
          (vec (concat [[:redirect {:op '>& :fd 0 :arg :stdin}]
                        [:redirect {:op '>& :fd 2 :arg :stderr}]]
                       (when-not end [[:redirect {:op '>& :fd 1 :arg :stdout}]])))
        redir-end
          [[:redirect {:op '>& :fd 1 :arg :stdout}]
           [:redirect {:op '>& :fd 2 :arg :stderr}]]]
    (concat
     ['-> (process-command cmd redir-begin)]
     (map process-pipeline-command pipeline)
     (when end [(process-pipeline-command end redir-end)]))))

(defn ^:no-doc process-pipeline-batch
  "Transform conformed pipeline specification in batch mode. Pipeline does not write to stdout by default but has a stream that can be redirected."
  [{:keys [cmd cmds]}]
  (concat
   (list '-> (process-command cmd))
   (map process-pipeline-command cmds)))

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
