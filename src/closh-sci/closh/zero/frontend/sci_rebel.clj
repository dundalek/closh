(ns closh.zero.frontend.sci-rebel
  (:gen-class)
  (:require [closh.zero.frontend.rebel :as rebel]
            [closh.zero.core :as closh.core]
            [closh.zero.utils.clojure-main-sci :as clojure-main]))

(defn help-opt
  "Print help text for main"
  [_ _]
  (println (-> (:doc (meta (var clojure-main/main)))
               (clojure.string/replace #"java -cp clojure\.jar clojure\.main" "closh-zero-sci"))))

(defn -main [& args]
  (if (= args '("--version"))
    (prn {:closh (closh.core/closh-version)
          :clojure (clojure-version)})
    (with-redefs [clojure-main/repl-opt rebel/repl
                  clojure-main/help-opt help-opt]
      (apply clojure-main/main args))))
