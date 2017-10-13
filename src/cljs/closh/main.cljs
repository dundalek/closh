(ns closh.main
  (:require [closh.core :refer [get-out-stream]]))

(def spawn (.-spawn (js/require "child_process")))

; (-> (shx "ls" "-a" "/home/me")
;     (pipe-map clojure.string/upper-case)
;     (pipe-filter #(= (first %) "."))
;     (pipe (shx "head"))
;     get-out-stream
;     (.pipe (.-stdout js/process)))

(def lumo "/home/me/.nvm/versions/node/v8.4.0/lib/node_modules/lumo-cljs/bin/lumo")

(def args
  #js["-d" "-e"
      "(require '[closh.core :refer
                    [read-command expand shx pipe pipe-map pipe-filter get-out-stream]])
       (require-macros '[closh.core :refer [sh]])
       (def _closh_out_stream_ (.createWriteStream (js/require \"fs\") nil #js{:fd 3}))"
      "-r"])

(def opts #js{:cwd (.cwd js/process)
              :stdio #js["pipe" "pipe" "pipe" (.-stdout js/process)]})

(def repl-process (spawn lumo args opts))
; (.pipe (aget repl-process "stdout") (.-stdout js/process))

; (.write (.-stdin repl-process) "(+ 1 2)\n")
; (.write (.-stdin repl-process) "(.write _closh_out_stream_ \"test\\n\")\n")
; (.write (.-stdin repl-process) "(-> (sh ls -l) get-out-stream (.pipe _closh_out_stream_))\n")

(def readline (js/require "readline"))

(def rl (.createInterface readline
         #js{:input js/process.stdin
             :output js/process.stdout
             :prompt "$ "}))

(-> rl
  (.on "line"
       (fn [input]
         (if (re-find #"^\s*#?\(" input)
           (.write (.-stdin repl-process) (str "(->> " input " prn-str (.write _closh_out_stream_))\n"))
           (.write (.-stdin repl-process) (str "(-> (sh " input ") get-out-stream (.pipe _closh_out_stream_))\n")))))
        ;  (js/console.log "Received:" line)
        ;  (.prompt rl)))
  (.on "close" #(.exit js/process 0)))

(.prompt rl)
