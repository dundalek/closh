(ns closh.main
  (:require [clojure.pprint :refer [pprint]]
            [clojure.string]
            [goog.object :as gobj]
            [lumo.repl]
            [closh.parser]
            [closh.compiler]
            [closh.zero.pipeline]
            [closh.zero.platform.io]
            [closh.zero.platform.util]
            [closh.zero.platform.process :as process]
            [closh.env]
            [closh.builtin]
            [closh.util]
            [closh.completion]
            [closh.zero.platform.eval :refer [execute-text execute-command-text]]
            [closh.core :refer [handle-line expand-alias expand-abbreviation]]
            [closh.history :refer [init-database add-history]]
            [closh.macros :refer-macros [sh sh-str sh-code sh-ok sh-seq sh-lines sh-value defalias defabbr defcmd]]))

(enable-console-print!)

(def ^:no-doc readline (js/require "readline"))
(def ^:no-doc child-process (js/require "child_process"))
(def ^:no-doc fs (js/require "fs"))
(def ^:no-doc os (js/require "os"))
(def ^:no-doc path (js/require "path"))

; (def util-binding (js/process.binding "util"))

(def ^:no-doc readline-tty-write readline.Interface.prototype._ttyWrite)

(def ^:no-doc initial-readline-state {:mode :input})
(def ^:no-doc readline-state (atom initial-readline-state))

(defn load-init-file
  "Loads init file."
  [init-path]
  (when (try (-> (fs.statSync init-path)
                 (.isFile))
             (catch :default _))
    (try (lumo.repl/execute-path init-path {})
         (catch :default e
           (js/console.error "Error while loading " init-path ":\n" e)))))

(defn restore-previous-state
  "Helper to restore the previously backed up readline state after search is canceled or finished."
  [state]
  (assoc state
    :history-state nil
    :mode :input
    :prompt (:previous-prompt state)
    :previous-line nil
    :previous-prompt nil
    :query nil
    :failed-search false))

(defn activate-search-state
  "Helper to update state when search is triggered. Mainly it backs up previous readline state so it can be later restored if search is canceled."
  [state rl search-mode]
  (if (:history-state state)
    (assoc state :search-mode search-mode
                 :mode (if (= search-mode :prefix) (:mode state) :search))
    (let [query (subs (.-line rl) 0 (.-cursor rl))
          mode (if (and (= search-mode :prefix)
                        (empty? query))
                 :input
                 :search)]
      (assoc state
        :mode mode
        :search-mode search-mode
        :query query
        :line ""
        :cursor 0
        :previous-prompt (.-_prompt rl)
        :previous-line (.-line rl)))))

(defn render-line
  "Renders the readline, first it sets the properties from state and then refreshes the line."
  [rl {:keys [line cursor prompt mode search-mode query failed-search]}]
  (when-not (nil? line) (aset rl "line" line))
  (when-not (nil? cursor) (aset rl "cursor" cursor))
  (when-let [p (if (= mode :search)
                 (let [kind (case search-mode
                              :prefix "history-prefix-search"
                              :substring "history-search"
                              "unknown-type-of-search")
                       label (if failed-search (str "failed " kind) kind)]
                   (str "(" label ")`" query "': "))
                 prompt)]
    (aset rl "_prompt" p))
  (._refreshLine rl))

(defn prompt
  "Prints prompt to a readline instance."
  [rl]
  (let [result (atom nil)
        process-fn (fn [_ value] (reset! result value))]
    (with-redefs [lumo.repl/process-1-2-3 process-fn]
      (execute-text "(closh-prompt)"))
    (doto rl
      (.setPrompt @result)
      (.prompt true))
    (with-redefs [lumo.repl/process-1-2-3 process-fn]
      (execute-text "(closh-title)"))
    (.write js/process.stdout (str "\033]0;" @result "\007"))))

;; TODO: Potencial race condition if latter history call returns before the previous one
;; Maybe some loading indicator?
(defn search-history-prev
  "Searches previous item in history."
  [{:keys [query history-state search-mode] :as state} rl]
  (closh.history/search-history-prev query history-state search-mode
    (fn [err data]
      (when err (js/console.log "Error searching history:" err))
      (swap! readline-state
        #(if-let [[line index] data]
           (assoc % :history-state index
                    :line line
                    :cursor (count line)
                    :failed-search false)
           (assoc % :mode :search ;; Make sure we are in search mode when search fails to display user a message
                    :failed-search true)))
      (render-line rl @readline-state)))
  state)

(defn search-history-next
  "Searches next item in history."
  [{:keys [query history-state search-mode] :as state} rl]
  (closh.history/search-history-next query history-state search-mode
    (fn [err data]
      (when err (js/console.log "Error searching history:" err))
      (swap! readline-state
        #(if-let [[line index] data]
           (assoc % :history-state index
                    :line line
                    :cursor (count line)
                    :failed-search false)
           (let [line (or (:previous-line %) "")
                 cursor (count line)]
             (assoc (restore-previous-state %)
                    :cursor cursor
                    :line line))))
      (render-line rl @readline-state)))
  state)

(defn key-value
  "Returns a canonical string for a key press, e.g. ctrl-r or ctrl-meta-shift-delete."
  [key]
  ;; escape seems to come with meta always switched on, so lets strip it for now
  (if (= (.-name key) "escape")
    "escape"
    (->>
      [(when (.-ctrl key) "ctrl")
       (when (.-meta key) "meta")
       (when (.-shift key) "shift")
       (.-name key)]
      (filter identity)
      (clojure.string/join "-"))))

(defn handle-keypress
  "Handles a keypress event by returning updated state. It returns nil when it did not handle the event, it that case falling back to default handler is recommended."
  [{:keys [query] :as state} rl c key]
  (case (:mode state)
   :input
   (case (key-value key)
     "up" (-> state
            (activate-search-state rl :prefix)
            (search-history-prev rl))
     "down" (if (:history-state state)
              (-> state
                (activate-search-state rl :prefix)
                (search-history-next rl))
              state)
     "ctrl-r" (-> state
                (activate-search-state rl :substring)
                (search-history-prev rl))
     nil)

   :search
   (case (key-value key)
     "up" (search-history-prev state rl)
     "down" (search-history-next state rl)
     ;; Accept current line
     "tab" (restore-previous-state state)
     ;; Accept and execute current line (execution is handled by caller)
     "return" (restore-previous-state state)
     "escape" (let [line (or (:previous-line state) "")
                    cursor (count line)]
                (assoc (restore-previous-state state)
                       :cursor cursor
                       :line line))
     ;; Cancel search and reset line input
     "ctrl-c" (assoc (restore-previous-state state)
                     :line ""
                     :cursor 0)
     ;; Search for previous entry (switches to substr search mode if necessary)
     "ctrl-r" (search-history-prev (assoc state :search-mode :substring) rl)
     ;; Search for next entry (switches to substr search mode if necessary)
     "ctrl-s" (search-history-next (assoc state :search-mode :substring) rl)
     ;; Default case - update search query based on typed character
     (if-let [q (when-not (or (.-meta key) (.-ctrl key))
                  (if (and (not (.-shift key)) (= (.-name key) "backspace"))
                    (.slice query 0 -1)
                    (str query c)))]
       (when (not= query q)
         (let [next-state (assoc state :query q
                                       :history-state nil)]
           (search-history-prev next-state rl)))))

   nil))

(defn -main
  "Starts closh REPL with prompt and readline."
  []
  (doto js/process
    ; ignore SIGQUIT like Bash
    (.on "SIGQUIT" (fn []))
    ; ignore SIGINT when not running a command (when running a command it already interupts execution with exception)
    (.on "SIGINT" (fn [])))
  (closh.zero.platform.eval/execute-text
    (str (pr-str closh.env/*closh-environment-init*)))
  (load-init-file (path.join (os.homedir) ".closhrc"))
  (let [rl (.createInterface readline
             #js{:input js/process.stdin
                 :output js/process.stdout
                 :completer closh.completion/complete
                 :prompt "$ "})]
    (aset rl "_ttyWrite"
      (fn [c key]
        (this-as self
          (if-let [state (handle-keypress @readline-state self c key)]
            (do
              (reset! readline-state state)
              (render-line rl state)
              ;; When return key is pressed pass it down to readline to execute the current line
              (when (= (key-value key) "return")
                (.call readline-tty-write self c key)))
            (if (= (.-cursor rl) (.-line.length rl))
              (case (key-value key)
                "return" (let [line (.-line rl)
                               expanded (expand-abbreviation line)]
                           (if (not= expanded line)
                             (doto rl
                               (gobj/set "line" expanded)
                               (gobj/set "cursor" (.-length expanded))
                               (._refreshLine))
                             (.call readline-tty-write self c key)))
                "space" (let [line (str (.-line rl) " ")
                              expanded (expand-abbreviation line)]
                            (doto rl
                              (gobj/set "line" expanded)
                              (gobj/set "cursor" (.-length expanded))
                              (._refreshLine)))
                (.call readline-tty-write self c key))
              (.call readline-tty-write self c key))))))
    (.catch (init-database)
     (fn [err]
       (js/console.error "Error initializing history database:" err)
       (process/exit 1)))
    (doto rl
      (.on "line"
        (fn [input]
          (.pause rl)
          (when-not (clojure.string/blank? input)
            (reset! readline-state initial-readline-state)
            (when-not (re-find #"^\s+" input)
              (add-history input (process/cwd)
                (fn [err] (when err (js/console.error "Error saving history:" err)))))
            ; (.startSigintWatchdog util-binding)
            (let [previous-mode (._setRawMode rl false)]
              (try
                (let [result (handle-line (expand-alias input) execute-command-text)]
                  (when-not (or (nil? result)
                                (instance? child-process.ChildProcess result)
                                (and (seq? result)
                                     (every? #(instance? child-process.ChildProcess %) result)))
                    (.write js/process.stdout (with-out-str (pprint result)))))
                (catch :default e
                  (js/console.error e)))
              (._setRawMode rl previous-mode)))
            ; (when (.stopSigintWatchdog util-binding)
            ;   (.emit rl "SIGINT")))
          (prompt rl)
          (.resume rl)))
      (.on "close" #(.exit js/process 0))
      ;; clear line on ctrl+c
      (.on "SIGINT" (fn []
                      (doto rl
                        (aset "line" "")
                        (aset "cursor" 0)
                        (._refreshLine))))
      (prompt))))

(set! *main-cli-fn* -main)
