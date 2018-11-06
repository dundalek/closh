(ns closh.zero.frontend.rebel
  (:gen-class)
  (:require [rebel-readline.clojure.main :refer [syntax-highlight-prn]]
            [rebel-readline.core :as core]
            [rebel-readline.clojure.line-reader :as clj-line-reader]
            [rebel-readline.jline-api :as api]
            [rebel-readline.clojure.service.local :as clj-service]
            [rebel-readline.tools :as tools]
            [clojure.string :as string]
            [clojure.main :refer [repl-requires]]
            [clojure.java.io :as jio]
            [closh.zero.env :refer [*closh-environment-init*]]
            [closh.zero.reader]
            [closh.zero.platform.process :refer [process?]]
            [closh.zero.frontend.main]
            [closh.zero.service.completion :refer [complete-shell]])
  (:import [org.jline.reader Completer ParsedLine LineReader]))

(defn repl-prompt []
  (try
    (eval '(print (closh-prompt)))
    (catch Exception e
      (println "Error printing prompt:" (:cause (Throwable->map e)))
      (println "Please check the definition of closh-prompt function in your ~/.closhrc")
      (print "$ "))))

(def opts {:prompt repl-prompt})

; rebel-readline.clojure.main/create-repl-read
(def create-repl-read
  (core/create-buffered-repl-reader-fn
   (fn [s] (clojure.lang.LineNumberingPushbackReader.
            (java.io.StringReader. s)))
   core/has-remaining?
   closh.zero.frontend.main/repl-read))

(defn repl-print
  [& args]
  (when-not (or (nil? (first args))
                (process? (first args)))
    (apply syntax-highlight-prn args)))

; rebel-readline.clojure.line-reader/clojure-completer
(defn clojure-completer []
  (proxy [Completer] []
    (complete [^LineReader reader ^ParsedLine line ^java.util.List candidates]
      (let [word (.word line)]
        (when (and
               (:completion @api/*line-reader*)
               (not (string/blank? word))
               (pos? (count word)))
          (let [options (let [ns' (clj-line-reader/current-ns)
                              context (clj-line-reader/complete-context line)]
                          (cond-> {}
                            ns'     (assoc :ns ns')
                            context (assoc :context context)))
                {:keys [cursor word-cursor line]} (meta line)
                paren-begin (= \( (get line (- cursor word-cursor 1)))
                shell-completions (->> (complete-shell (subs line 0 cursor))
                                       (map (fn [candidate] {:candidate candidate})))
                clj-completions (clj-line-reader/completions word options)]
            (->>
             (if paren-begin
               (concat
                 clj-completions
                 shell-completions)
               (concat
                 shell-completions
                 clj-completions))
             (map #(clj-line-reader/candidate %))
             (take 10)
             (.addAll candidates))))))))

(defn load-init-file
  "Loads init file."
  [init-path]
  (when (.isFile (jio/file init-path))
    (eval `(load-file ~init-path))))

(defn handle-sigint-form []
  `(let [thread# (Thread/currentThread)]
     (clojure.repl/set-break-handler! (fn [signal#] (.stop thread#)))))

(defn -main []
  (core/ensure-terminal
    (core/with-line-reader
      (doto
        (clj-line-reader/create
          (clj-service/create
            (when api/*line-reader* @api/*line-reader*))
          {:completer (clojure-completer)})
        (.setVariable LineReader/HISTORY_FILE (str (jio/file (System/getProperty "user.home") ".closh" "history"))))
      (binding [*out* (api/safe-terminal-writer api/*line-reader*)]
        (when-let [prompt-fn (:prompt opts)]
          (swap! api/*line-reader* assoc :prompt prompt-fn))
        ; (println (core/help-message))
        (apply
          clojure.main/repl
          (-> {:init (fn []
                        (in-ns 'user)
                        (apply require repl-requires)
                        (eval *closh-environment-init*)
                        (try
                          (load-init-file (.getCanonicalPath (jio/file (System/getProperty "user.home") ".closhrc")))
                          (catch Exception e
                            (binding [*out* *err*]
                              (println "Error while loading init file ~/.closhrc:\n" e)))))
               :print repl-print
               :read (create-repl-read)
               :eval (fn [form] (eval `(do ~(handle-sigint-form) ~form)))}
              (merge opts {:prompt (fn [])})
              seq
              flatten))))))
