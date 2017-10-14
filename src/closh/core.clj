(ns closh.core
  (:require [clojure.string]))

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

(defmacro sh [& tokens]
  (->> tokens
       (partition-by pipe-set)
       (map (fn [s]
              (cond
                (pipe-set (first s)) (first s)
                ; for now just get the first function
                (list? (first s)) (if (= (count (first s)) 1)
                                    (ffirst s)
                                    (first s))
                ; (list? (first s)) s
                :else (apply command s))))
       (handle-pipes)))
