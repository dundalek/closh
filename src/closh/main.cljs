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

(defn load-init-file
  "Loads init file."
  [init-path]
  (when (try (-> (fs.statSync init-path)
                 (.isFile))
             (catch :default _))
    (try (lumo.repl/execute-path init-path {})
         (catch :default e
           (js/console.error "Error while loading " init-path ":\n" e)))))

(defn prompt
  "Prints prompt to a readline instance."
  [rl]
  (doto rl
    (.setPrompt (execute-text "(closh-prompt)"))
    (.prompt true)))

(defn -main
  "Starts closh REPL with prompt and readline."
  []
  (patch-reader)
  (load-init-file (path.join (os.homedir) ".closhrc"))
  (let [rl (.createInterface readline
             #js{:input js/process.stdin
                 :output js/process.stdout
                 :prompt "$ "})]
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
