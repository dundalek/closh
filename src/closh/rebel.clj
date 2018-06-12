(ns closh.rebel
  (:require [rebel-readline.clojure.main :refer [syntax-highlight-prn create-repl-read]]
            [rebel-readline.core :as core]
            [rebel-readline.clojure.line-reader :as clj-line-reader]
            [rebel-readline.jline-api :as api]
            [rebel-readline.clojure.service.local :as clj-service]))

(def opts {})
(def clj-repl clojure.main/repl)

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
          clj-repl
          (-> {:print syntax-highlight-prn
               :read (create-repl-read)}
              (merge opts {:prompt (fn [])})
              seq
              flatten))))))
