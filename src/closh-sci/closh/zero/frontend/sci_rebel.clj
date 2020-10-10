(ns closh.zero.frontend.sci-rebel
  (:gen-class)
  (:require [closh.zero.frontend.rebel :as rebel]
            [closh.zero.core :as closh.core]
            ;; Dependencies loaded lazily by rebel-readline, requireing them for native-image
            [cljfmt.core]
            [compliment.core]))

(defn -main [& args]
  (if (= args '("--version"))
    (prn {:closh (closh.core/closh-version)
          :clojure (clojure-version)})
    #_(apply main args)
    (rebel/repl nil nil)))
