(ns closh.zero.main
  (:require [lumo.repl]
            [closh.zero.parser]
            [closh.zero.compiler]
            [closh.zero.pipeline]
            [closh.zero.platform.io]
            [closh.zero.platform.util]
            [closh.zero.platform.process :as process]
            [closh.zero.env]
            [closh.zero.builtin]
            [closh.zero.util]
            [closh.zero.platform.eval :refer [execute-text]]
            [closh.zero.service.history :refer [init-database]]
            [closh.zero.macros :refer-macros [sh sh-str sh-code sh-ok sh-seq sh-lines sh-value defalias defabbr defcmd]]
            [closh.zero.frontend.node-readline]
            #?@(:cljs [[fs] [os] [path]])))

(enable-console-print!)

(defn load-init-file
  "Loads init file."
  [init-path]
  (when (try (-> (fs/statSync init-path)
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
    (str (pr-str closh.zero.env/*closh-environment-requires*)
         (pr-str closh.zero.env/*closh-environment-init*)))
  (load-init-file (path/join (os/homedir) ".closhrc"))
  (.catch (init-database)
   (fn [err]
     (js/console.error "Error initializing history database:" err)
     (process/exit 1)))
  (closh.zero.frontend.node-readline/-main))
