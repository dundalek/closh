(ns closh.builtin
  (:require [clojure.string]))

(defn cd [& args]
  (process.chdir (first (flatten args))))

(defn exit [& args]
  (process.exit (first (flatten args))))

(def quit exit)
