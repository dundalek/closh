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

(def readline-state (atom {:history-index -1}))

(defn load-init-file
  "Loads init file."
  [init-path]
  (when (try (-> (fs.statSync init-path)
                 (.isFile))
             (catch :default _))
    (try (lumo.repl/execute-path init-path {})
         (catch :default e
           (js/console.error "Error while loading " init-path ":\n" e)))))

(defn search-history-prev [{:keys [history-index] :as state} rl]
  (if (< (inc history-index)
         (.-history.length rl))
    (let [index (inc history-index)
          line (aget rl "history" index)
          cursor (.-length line)]
      (assoc state :history-index index
                   :line line
                   :cursor cursor))
    state))

(defn search-history-next [{:keys [history-index] :as state} rl]
  (cond
    (pos? history-index)
    (let [index (dec history-index)
          line (aget rl "history" index)
          cursor (.-length line)]
      (assoc state :history-index index
                   :line line
                   :cursor cursor))

    (zero? history-index)
    (assoc state :history-index -1
                 :cursor 0
                 :line "")

    :default state))

(defn render-line [rl {:keys [line cursor]}]
  (when-not (nil? line) (aset rl "line" line))
  (when-not (nil? cursor) (aset rl "cursor" cursor))
  (._refreshLine rl))

(defn prompt
  "Prints prompt to a readline instance."
  [rl]
  (doto rl
    (.setPrompt (execute-text "(closh-prompt)"))
    (.prompt true)))

(defn handle-keypress [rl c key]
  (when-not (or (.-shift key) (.-ctrl key) (.-meta key))
    (case (.-name key)
      "up" (do
             (swap! readline-state search-history-prev rl)
             (render-line rl @readline-state)
             true)
      "down" (do
               (swap! readline-state search-history-next rl)
               (render-line rl @readline-state)
               true)
      false)))

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
          (when-not (handle-keypress self c key)
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
