(ns closh.zero.parser
  (:require #?(:cljs [closh.zero.parser-spec :as parser]
               :clj [closh.zero.parser-squarepeg :as parser])))

(def parse parser/parse)
