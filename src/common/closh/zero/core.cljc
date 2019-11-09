(ns closh.zero.core
  (:require [clojure.string]
            [closh.zero.platform.io :refer [glob *stderr*]]
            [closh.zero.platform.process :as process]
            [closh.zero.pipeline :refer [process-value]]
            [closh.zero.env :refer [*closh-aliases* *closh-abbreviations*]]))

#?(:clj (set! *warn-on-reflection* true))

(def command-not-found-bin "/usr/lib/command-not-found")

(defn expand-variable
  "Expands env variable, it does not look inside string."
  [s]
  (if (re-find #"^\$" s)
    (process/getenv (subs s 1))
    s))

(defn expand-tilde
  "Expands tilde character to a path to user's home directory."
  [s]
  (clojure.string/replace-first s #"^~" (process/getenv "HOME")))

(defn expand-filename
  "Expands filename based on globbing patterns"
  [s]
  (glob s (process/cwd)))

(defn expand-redirect
  "Expand redirect targets. It does tilde and variable expansion."
  [s]
  (-> s
      (expand-tilde)
      (expand-variable)))

; Bash: Partial quote (allows variable and command expansion)
(defn expand-partial
  "Partially expands parameter which is used when parameter is quoted as string. It only does variable expansion."
  [s]
  (if-let [result (expand-variable s)]
    (list result)
    (list)))

; Bash: The order of expansions is: brace expansion; tilde expansion, parameter and variable expansion, arithmetic expansion, and command substitution (done in a left-to-right fashion); word splitting; and filename expansion.
(defn expand
  "Expands command-line parameter.

  The order of expansions is variable expansion, tilde expansion and filename expansion."
  [s]
  (if-let [x (expand-variable s)]
    (-> x
        expand-tilde
        expand-filename)
    (list)))

(defn expand-command
  "Expands first command token."
  [s]
  (first (expand s)))

(defn get-command-suggestion
  "Get suggestion for a missing command using command-not-found utility."
  [cmdname]
  (try
    (->
     (process/shx command-not-found-bin ["--no-failure-msg" cmdname])
     #?(:cljs (.on "error" (fn [])))
     (process-value)
     (:stderr))
    (catch #?(:cljs :default :clj Exception) _)))

(defn shx
  "Executes a command as child process."
  ([cmd] (shx cmd []))
  ([cmd args] (shx cmd args {}))
  ([cmd args opts]
   #?(:cljs (doto
             (process/shx cmd args opts)
              (.on "error"
                   (fn [err]
                     (case (.-errno err)
                       "ENOENT" (let [suggestion (get-command-suggestion cmd)]
                                  (when-not (clojure.string/blank? suggestion)
                                    (.write *stderr* suggestion))
                                  (.write *stderr* (str cmd ": command not found\n")))
                       (.write *stderr* (str "Unexpected error:\n" err "\n"))))))
      :clj (try
             (process/shx cmd args opts)
             (catch java.io.IOException e
               (let [suggestion (get-command-suggestion cmd)]
                 (when-not (clojure.string/blank? suggestion)
                   (.print ^java.io.PrintStream *stderr* suggestion))
                 (.println ^java.io.PrintStream *stderr* (str cmd ": command not found"))
                 #_(println "STACKTRACE:")
                 #_(.printStackTrace e)))
             (catch Exception ex
               (.println ^java.io.PrintStream *stderr* (str "Unexpected error:\n" ex)))))))

(defn expand-alias
  ([input] (expand-alias @*closh-aliases* input))
  ([aliases input]
   (let [token (re-find #"[^\s]+" input)
         alias (get aliases token)]
     (if alias
       (clojure.string/replace-first input #"[^\s]+" alias)
       input))))

(defn expand-abbreviation
  ([input] (expand-abbreviation @*closh-abbreviations* input))
  ([aliases input]
   (let [token (re-find #"[^\s]+" input)
         alias (get aliases token)]
     (if (and alias
              (= (clojure.string/trim input) token))
       (clojure.string/replace-first input #"[^\s]+" alias)
       input))))
