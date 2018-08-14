(ns closh.main
  (:require [lumo.repl]
            [closh.parser]
            [closh.compiler]
            [closh.zero.pipeline]
            [closh.zero.platform.io]
            [closh.zero.platform.util]
            [closh.zero.platform.process :as process]
            [closh.env]
            [closh.builtin]
            [closh.util]
            [closh.zero.platform.eval :refer [execute-text]]
            [closh.zero.service.history :refer [init-database]]
            [closh.macros :refer-macros [sh sh-str sh-code sh-ok sh-seq sh-lines sh-value defalias defabbr defcmd]]
            [closh.zero.frontend.node-readline]))

(enable-console-print!)

(def ^:no-doc fs (js/require "fs"))
(def ^:no-doc os (js/require "os"))
(def ^:no-doc path (js/require "path"))
; (def util-binding (js/process.binding "util"))

(defn load-init-file
  "Loads init file."
  [init-path]
  (when (try (-> (fs.statSync init-path)
                 (.isFile))
             (catch :default _))
    (try (lumo.repl/execute-path init-path {})
         (catch :default e
           (js/console.error "Error while loading " init-path ":\n" e)))))

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
  (.catch (init-database)
   (fn [err]
     (js/console.error "Error initializing history database:" err)
     (process/exit 1)))
  (closh.zero.frontend.node-readline/-main))
