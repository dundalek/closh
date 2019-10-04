(ns closh.zero.utils.sci
  (:require [clojure.java.io :as io]
            [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :refer [push-back-reader]]
            [sci.core :as sci]
            [closh.zero.env]))

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
    (let [prdr (push-back-reader rdr)
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

(def bindings
  (merge
    (closh-bindings)
    (closh-macro-bindings)
    {'deref deref
     'clojure.core/deref deref
     'swap! swap!
     'clojure.core/swap! swap!
     'Math/sqrt #(Math/sqrt %)
     'closh.zero.env/*closh-commands* closh.zero.env/*closh-commands*}))

(def sci-env (atom {}))

(defn custom-eval [form]
  #_(eval form)
  (sci/eval-string (pr-str form) {:bindings bindings
                                  :env sci-env}))