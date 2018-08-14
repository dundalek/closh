(ns closh.zero.frontend.rebel-readline
  (:require [rebel-readline.clojure.main :refer [syntax-highlight-prn]]
            [rebel-readline.core :as core]
            [rebel-readline.clojure.line-reader :as clj-line-reader]
            [rebel-readline.jline-api :as api]
            [rebel-readline.clojure.service.local :as clj-service]
            [clojure.main :refer [repl-requires]]
            [clojure.java.io :as jio]
            [closh.env :refer [*closh-environment-init*]]
            [closh.reader]
            [closh.zero.platform.process :refer [process?]]
            [closh.zero.frontend.clojure-main-repl]))

(defn repl-prompt []
  (eval '(print (user/closh-prompt))))

(def opts {:prompt repl-prompt})

; rebel-readline.clojure.main/create-repl-read
(def create-repl-read
  (core/create-buffered-repl-reader-fn
   (fn [s] (clojure.lang.LineNumberingPushbackReader.
            (java.io.StringReader. s)))
   core/has-remaining?
   closh.zero.frontend.clojure-main-repl/repl-read))

(defn repl-print
  [& args]
  (when-not (or (nil? (first args))
                (process? (first args)))
    (apply syntax-highlight-prn args)))

(defn load-init-file
  "Loads init file."
  [init-path]
  (when (.isFile (jio/file init-path))
    (load-file init-path)))

(defn -main []
  (core/ensure-terminal
    (core/with-line-reader
      (clj-line-reader/create
        (clj-service/create
          (when api/*line-reader* @api/*line-reader*)))
      (binding [*out* (api/safe-terminal-writer api/*line-reader*)]
        (when-let [prompt-fn (:prompt opts)]
          (swap! api/*line-reader* assoc :prompt prompt-fn))
        (println (core/help-message))
        (apply
          clojure.main/repl
          (-> {:init (fn []
                        (apply require repl-requires)
                        (eval *closh-environment-init*)
                        (try
                          (load-init-file (.getCanonicalPath (jio/file (System/getProperty "user.home") ".closhrc")))
                          (catch Exception e
                            (binding [*out* *err*]
                              (println "Error while loading init file ~/.closhrc:\n" e)))))
               :print repl-print
               :read (create-repl-read)}
              (merge opts {:prompt (fn [])})
              seq
              flatten))))))
