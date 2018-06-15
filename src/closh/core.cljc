(ns closh.core
  (:require [clojure.string]
            [goog.object :as gobj]
            [closh.builtin :refer [getenv]]
            [closh.zero.platform.io :refer [glob open-io-streams]]
            [closh.zero.platform.process :as process :refer [process?]]
            [closh.zero.pipeline :refer [pipeline-value wait-for-pipeline]]
            [closh.env :refer [*closh-aliases* *closh-abbreviations*]]))

(def ^:no-doc fs (js/require "fs"))
(def ^:no-doc child-process (js/require "child_process"))

(def command-not-found-bin "/usr/lib/command-not-found")

(defn expand-variable
  "Expands env variable, it does not look inside string."
  [s]
  (if (re-find #"^\$" s)
    (getenv (subs s 1))
    s))

(defn expand-tilde
  "Expands tilde character to a path to user's home directory."
  [s]
  (clojure.string/replace-first s #"^~" (getenv "HOME")))

(defn expand-filename
  "Expands filename based on globbing patterns"
  [s]
  (glob s))

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

(defn get-command-suggestion
  "Get suggestion for a missing command using command-not-found utility."
  [cmdname]
  (try
    (fs.accessSync command-not-found-bin fs.constants.X_OK)
    (-> (child-process.spawnSync command-not-found-bin #js["--no-failure-msg" cmdname] #js{:encoding "utf-8"})
        (.-stderr)
        (clojure.string/trim))
    (catch :default _)))

(defn handle-spawn-error
  "Formats and prints error from spawn."
  [err]
  (case (.-errno err)
    "ENOENT" (let [cmdname (.-path err)
                   suggestion (get-command-suggestion cmdname)]
               (when-not (clojure.string/blank? suggestion)
                 (js/console.error suggestion))
               (js/console.error (str cmdname ": command not found")))
    (js/console.error "Unexpected error:\n" err)))

(defn shx
  "Executes a command as child process."
  ([cmd] (shx cmd []))
  ([cmd args] (shx cmd args {}))
  ([cmd args opts]
   (doto
     (child-process.spawn
       cmd
       (apply array (flatten args))
       #js{:stdio (open-io-streams (:redir opts))})
     (.on "error" handle-spawn-error))))

(defn expand-alias
  ([input] (expand-alias *closh-aliases* input))
  ([aliases input]
   (let [token (re-find #"[^\s]+" input)
         alias (get aliases token)]
     (if alias
       (clojure.string/replace-first input #"[^\s]+" alias)
       input))))

(defn expand-abbreviation
  ([input] (expand-alias *closh-abbreviations* input))
  ([aliases input]
   (let [token (re-find #"[^\s]+" input)
         alias (get aliases token)]
     (if (and alias
              (= (clojure.string/trim input) token))
       (clojure.string/replace-first input #"[^\s]+" alias)
       input))))

(defn handle-line
  "Parses given string, evals and waits for execution to finish. Pass in the `eval-cljs` function that evals forms in desired context."
  [input eval-cljs]
  (-> input
    (eval-cljs)
    (wait-for-pipeline)))
