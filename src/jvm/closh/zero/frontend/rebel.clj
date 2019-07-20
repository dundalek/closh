(ns closh.zero.frontend.rebel
  (:gen-class)
  (:require [rebel-readline.clojure.main :refer [syntax-highlight-prn]]
            [rebel-readline.core :as core]
            [rebel-readline.clojure.line-reader :as clj-line-reader]
            [rebel-readline.jline-api :as api]
            [rebel-readline.clojure.service.local :as clj-service]
            [clojure.string :as string]
            [clojure.java.io :as jio]
            [closh.zero.env :refer [*closh-environment-requires* *closh-environment-init*] :as env]
            [closh.zero.reader]
            [closh.zero.platform.process :refer [process?]]
            [closh.zero.frontend.main :as main]
            [closh.zero.service.completion :refer [complete-shell]]
            [closh.zero.utils.clojure-main :refer [repl-requires] :as clojure-main])
  (:import [org.jline.reader Completer ParsedLine LineReader]))

(defn repl-prompt []
  (try
    (eval '(print (closh-prompt)))
    (catch Exception e
      (println "Error printing prompt:" (:cause (Throwable->map e)))
      (println "Please check the definition of closh-prompt function in your ~/.closhrc")
      (print "$ ")))
  (let [title
        (try
          (eval '(closh-title))
          (catch Exception e
            (str "closh: Error in (closh-title): " (:cause (Throwable->map e)))))]
    (.print System/out (str "\u001b]0;" title "\u0007"))))

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
                (identical? (first args) env/success)
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

(defn repl [[_ & args] inits]
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
                        (clojure-main/initialize args inits)
                        (in-ns 'user)
                        (apply require repl-requires)
                        (in-ns 'user)
                        (eval *closh-environment-requires*)
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

(defn -main [& args]
  (with-redefs [clojure-main/load-script main/load-script
                clojure-main/eval-opt main/eval-opt
                clojure-main/repl-opt repl
                clojure-main/help-opt main/help-opt
                clojure.core/load-reader main/load-reader]
    (apply clojure-main/main args)))
