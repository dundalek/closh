(ns closh.zero.utils.sci
  (:require [sci.core :as sci]
            [closh.zero.pipeline :as pipeline]
            [closh.zero.core :as closh-core]
            [closh.zero.env :as env]
            [closh.zero.util :refer [thread-stop]]
            [clojure.repl :as repl])
  (:import (java.io PushbackReader)))

(comment
  (defmacro closh-requires []
    closh.zero.env/*closh-environment-requires*)

  (defmacro closh-bindings []
    (->> closh.zero.env/*closh-environment-requires*
         (drop 1)
         (mapcat (fn [[_ [namespace & opts]]]
                   (when (not= namespace 'closh.zero.macros)
                     (let [{:keys [as refer]} (apply hash-map opts)]
                       (concat
                        (for [x refer]
                          [x (symbol (str namespace) (str x))])
                        (->>
                         (ns-publics namespace)
                         (map (fn [[k v]]
                                [(symbol (str namespace) (str k))
                                 (symbol (str namespace) (str k))])))
                        (when as
                          (->>
                           (ns-publics namespace)
                           (map (fn [[k v]]
                                  [(symbol (str as) (str k))
                                   (symbol (str as) (str k))])))))))))
         (map (fn [[k v]]
                [(list 'quote k) v]))
         (into {})))

  (defmacro closh-macro-bindings []
    (with-open [rdr (io/reader "src/common/closh/zero/macros.cljc")]
      (let [prdr (PushbackReader. rdr)
            eof (Object.)
            opts {:eof eof :read-cond :allow :features #{:clj}}]
        (loop [bindings {}]
          (let [form (reader/read opts prdr)]
            (cond (= form eof) bindings

                  (= (first form) 'defmacro)
                  (let [name (second form)
                        fn-form `(with-meta
                                   (fn ~name ~@(drop-while #(not (vector? %)) form))
                                   {:sci/macro true})]
                    (recur (assoc bindings
                                  (list 'quote name) fn-form
                                  (list 'quote (symbol "closh.zero.macros" (str name))) fn-form)))

                  :else (recur bindings)))))))

  (closh-requires)

  #_(def bindings
    (merge
     (closh-bindings)
     (closh-macro-bindings)
     {
      ;; 'thread-stop thread-stop
      ;; 'clojure.repl/set-break-handler! clojure.repl/set-break-handler!
      ;; 'closh.zero.env/*closh-commands* closh.zero.env/*closh-commands*
      }))
  )

(declare ctx)

(defn load-file* [file]
  (let [s (slurp file)]
    (sci/eval-string s ctx)))

(def sci-env (atom {}))

(def bindings {'deref deref
               'clojure.core/deref deref
               'swap! swap!
               'clojure.core/swap! swap!
               'print print
               'println println
               'load-file load-file*
               'Math/sqrt #(Math/sqrt %)
               'java.lang.Thread/currentThread #(Thread/currentThread)
               'thread-stop thread-stop
               'clojure.repl/set-break-handler! repl/set-break-handler!
               'closh.zero.env/*closh-commands* env/*closh-commands*})

(def ctx {:bindings bindings
          :namespaces {'clojure.core {'println println}
                       'closh.zero.pipeline {'pipe pipeline/pipe
                                             'redir pipeline/redir
                                             'wait-for-pipeline pipeline/wait-for-pipeline}
                       'closh.zero.core {'shx closh-core/shx
                                         'expand-command closh-core/expand-command
                                         'expand closh-core/expand} }
          :env sci-env})

(defn sci-eval [form]
  ;; (prn "EVAL FORM" form)
  (sci/eval-string (pr-str form) ctx))
