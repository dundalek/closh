(ns closh.zero.platform.eval
  (:refer-clojure :exclude [eval]))

(defmacro def-eval []
  (if (System/getenv "__CLOSH_USE_SCI_EVAL__")
    `(do (require 'closh.zero.utils.sci)
         (def ~'eval closh.zero.utils.sci/sci-eval))
    `(def ~'eval clojure.core/eval)))

(def-eval)

(defmacro eval-closh-requires []
  (when-not (System/getenv "__CLOSH_USE_SCI_EVAL__")
    `(eval closh.zero.env/*closh-environment-requires*)))
