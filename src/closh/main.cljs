(ns closh.main
  (:require [clojure.tools.reader]
            [clojure.tools.reader.impl.commons]
            [clojure.pprint :refer [pprint]]
            [clojure.string]
            ; [lumo.io]
            [lumo.repl]
            [closh.parser]
            [closh.builtin]
            [closh.eval :refer [execute-text]]
            [closh.core :refer [handle-line]]
            [closh.history :refer [init-database load-history add-history]])
  (:require-macros [alter-cljs.core :refer [alter-var-root]]
                   [closh.reader :refer [patch-reader]]
                   [closh.core :refer [sh]]))

(enable-console-print!)

(def ^:no-doc readline (js/require "readline"))
(def ^:no-doc child-process (js/require "child_process"))
(def ^:no-doc fs (js/require "fs"))
(def ^:no-doc os (js/require "os"))
(def ^:no-doc path (js/require "path"))

(def readline-tty-write readline.Interface.prototype._ttyWrite)

(def readline-state (atom {:history-index -1
                           :mode :input}))

(defn load-init-file
  "Loads init file."
  [init-path]
  (when (try (-> (fs.statSync init-path)
                 (.isFile))
             (catch :default _))
    (try (lumo.repl/execute-path init-path {})
         (catch :default e
           (js/console.error "Error while loading " init-path ":\n" e)))))

(defn restore-previous-state [state]
  (assoc state
    :history-index -1
    :mode :input
    :prompt (:previous-prompt state)
    :previous-line nil
    :previous-prompt nil
    :query nil
    :failed-search false))

(defn activate-search-state [state rl search-mode]
  (assoc state
    :mode :search
    :previous-prompt (.-_prompt rl)
    :previous-line (.-line rl)
    :query (.-line rl)
    :search-mode search-mode))

(defn search-history-prev [{:keys [history-index query search-mode] :as state} rl]
  (let [comp-fn (if (= search-mode :prefix) clojure.string/starts-with? clojure.string/includes?)]
    (if-let [index
             (loop [i (inc history-index)]
               (cond (>= i (.-history.length rl)) nil
                     (comp-fn (aget rl "history" i) query) i
                     :else (recur (inc i))))]
      (let [line (aget rl "history" index)
            cursor (.-length line)]
        (assoc state :history-index index
                     :line line
                     :cursor cursor
                     :failed-search false))
      (assoc state :failed-search true))))

(defn search-history-next [{:keys [history-index query search-mode] :as state} rl]
  (let [comp-fn (if (= search-mode :prefix) clojure.string/starts-with? clojure.string/includes?)]
    (if-let [index
             (loop [i (dec history-index)]
               (cond (neg? i) nil
                     (comp-fn (aget rl "history" i) query) i
                     :else (recur (dec i))))]
      (let [line (aget rl "history" index)
            cursor (.-length line)]
        (assoc state :history-index index
                     :line line
                     :cursor cursor
                     :failed-search false))
      (let [line (or (:previous-line state) "")
            cursor (.-length line)]
        (assoc (restore-previous-state state)
               :cursor cursor
               :line line)))))

(defn render-line [rl {:keys [line cursor prompt mode search-mode query failed-search]}]
  (when-not (nil? line) (aset rl "line" line))
  (when-not (nil? cursor) (aset rl "cursor" cursor))
  (when-let [p (if (and (= mode :search) (not (empty? query)))
                 (let [kind (case search-mode
                              :prefix "history-prefix-search"
                              :substr "history-search"
                              "unknown-type-of-search")
                       label (if failed-search (str "failed " kind) kind)]
                   (str "(" label ")`" query "': "))
                 prompt)]
    (aset rl "_prompt" p))
  (._refreshLine rl))

(defn prompt
  "Prints prompt to a readline instance."
  [rl]
  (doto rl
    (.setPrompt (execute-text "(closh-prompt)"))
    (.prompt true)))

(defn key-value [key]
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

(defn handle-keypress [{:keys [query history-index] :as state} rl c key]
  ; (assoc state :line (key-value key)))
; (comment
  (case (:mode state)
   :input
   (case (key-value key)
     "up" (-> state
            (activate-search-state rl :prefix)
            (search-history-prev rl))
     "ctrl-r" (-> state
                (activate-search-state rl :substr)
                (search-history-prev rl))
     nil)

   :search
   (case (key-value key)
     "up" (search-history-prev state rl)
     "down" (if (zero? history-index)
              (let [line (or (:previous-line state) "")
                    cursor (.-length line)]
                (assoc (restore-previous-state state)
                       :cursor cursor
                       :line line))
              (search-history-next state rl))
     ;; Accept current line
     "tab" (restore-previous-state state)
     ;; Accept and execute current line
     ;; TODO: Execute
     "enter" (restore-previous-state state)
     ;; Cancel search
     "escape" (let [line (or (:previous-line state) "")
                    cursor (.-length line)]
                (assoc (restore-previous-state state)
                       :cursor cursor
                       :line line))
     ;; Cancel search and reset line input
     "ctrl-c" (assoc (restore-previous-state state)
                     :line ""
                     :cursor 0)
     ;; Search for previous entry (switches to substr search mode if necessary)
     "ctrl-r" (search-history-prev (assoc state :search-mode :substr) rl)
     ;; Default case - update search query based on typed character
     (if-let [q (when (not (or (.-meta key) (.-ctrl key)))
                  (if (and (not (.-shift key)) (= (.-name key) "backspace"))
                    (.slice query 0 -1)
                    (str query c)))]
       (when (not= query q)
         (let [next-state (assoc state :query q
                                       :history-index -1)]
           (search-history-prev next-state rl)))))

   nil))

(defn -main
  "Starts closh REPL with prompt and readline."
  []
  (patch-reader)
  (load-init-file (path.join (os.homedir) ".closhrc"))
  (let [rl (.createInterface readline
             #js{:input js/process.stdin
                 :output js/process.stdout
                 :prompt "$ "})]
    (aset rl "_ttyWrite"
      (fn [c key]
        (this-as self
          (if-let [state (handle-keypress @readline-state self c key)]
            (do
              (reset! readline-state state)
              (render-line rl state))
            (.call readline-tty-write self c key)))))
    (init-database
     (fn [err]
       (if err
         (do (js/console.error "Error initializing database:" err)
             (js/process.exit 1))
         (load-history
           (fn [err rows]
             (if err (js/console.error "Error loading history:" err)
                     (doseq [r rows] (.push (.-history rl) (.-command r)))))))))
    (doto rl
      (.on "line"
        (fn [input]
          (.pause rl)
          (when (not (clojure.string/blank? input))
            (when-not (re-find #"^\s+" input)
              (add-history input (js/process.cwd)
                (fn [err] (when err (js/console.error "Error saving history:" err)))))
            (try
              (let [result (handle-line input execute-text)]
                (when-not (or (nil? result)
                              (instance? child-process.ChildProcess result)
                              (and (seq? result)
                                   (every? #(instance? child-process.ChildProcess %) result)))
                  (.write js/process.stdout (with-out-str (pprint result)))))
              (catch :default e
                (js/console.error e))))
          (prompt rl)
          (.resume rl)))
      (.on "close" #(.exit js/process 0))
      (prompt))))

(set! *main-cli-fn* -main)
