(ns closh.zero.parser
  (:require [clojure.set]
            [clojure.spec.alpha :as s]))

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

(defn parse [coll]
  (s/conform ::cmd-list coll))
