(ns closh.main)
  ; (:require [cljs.reader]))
             ;  [closh.core :refer [expand shx pipe pipe-map pipe-filter get-out-stream]]))
             ; [lumo.repl])
  ; (:require-macros [closh.core :refer [sh]]))

; (defn read-command [input]
;   (let [s (if (re-find #"^\s*#?\(" input)
;             input
;             (str "(sh " input ")"))]
;     (read-string s)))


; (def spawn (.-spawn (js/require "child_process")))

; (-> (shx "ls" "-a" "/home/me")
;     (pipe-map clojure.string/upper-case)
;     (pipe-filter #(= (first %) "."))
;     (pipe (shx "head"))
;     get-out-stream
;     (.pipe (.-stdout js/process)))

; (def lumo "/home/me/.nvm/versions/node/v8.4.0/lib/node_modules/lumo-cljs/bin/lumo")
;
; (def args
;   #js["-d" "-e"
;       "(require '[closh.core :refer
;                     [read-command expand shx pipe pipe-map pipe-filter get-out-stream]])
;        (require-macros '[closh.core :refer [sh]])
;        (def _closh_out_stream_ (.createWriteStream (js/require \"fs\") nil #js{:fd 3}))"
;       "-r"])
;
; (def opts #js{:cwd (.cwd js/process)
;               :stdio #js["pipe" "pipe" "pipe" (.-stdout js/process)]})
;
; (def repl-process (spawn lumo args opts))
; (.pipe (aget repl-process "stdout") (.-stdout js/process))

; (.write (.-stdin repl-process) "(+ 1 2)\n")
; (.write (.-stdin repl-process) "(.write _closh_out_stream_ \"test\\n\")\n")
; (.write (.-stdin repl-process) "(-> (sh ls -l) get-out-stream (.pipe _closh_out_stream_))\n")

; (def readline (js/require "readline"))
;
; (def rl (.createInterface readline
;          #js{:input js/process.stdin
;              :output js/process.stdout
;              :prompt "$ "}))
;
; ; (defn handle-line [input]
; ;   (->> input
; ;        (cljs.reader/read-string)
; ;        (lumo.core/eval)
; ;        (prn-str)
; ;        (.write js/process.stdout)))
;
; (-> rl
;   (.on "line"
;        (fn [input]
;         ;  (.nextTick js/process (partial handle-line input))))
;       ;   ;  (if (re-find #"^\s*#?\(" input)
;       ;     ;  (js/console.log "*" input "*")
;       ;     ;  (.write js/process.stdout (prn-str (lumo.repl/eval (cljs.reader/read-string input))))
;       ;     ;  (.write js/process.stdout (prn-str (lumo.repl/eval '(+ 1 4)))
;        ;
;        ;
;          (.write js/process.stdout
;            (-> input
;                 (cljs.reader/read-string)
;                 ; (lumo.repl/eval "closh.main")
;                 (lumo.repl/eval)
;                 (prn-str)))))
;       ;           ))))
;           ;  (-> (str "sh " input ")")
;           ;      (cljs.reader/read-string)
;           ;      (lumo.core/eval)
;           ;      (get-out-stream)
;           ;      (.pipe js/process.stdout))))
;           ;  (.write (.-stdin repl-process) (str "(->> " input " prn-str (.write _closh_out_stream_))\n"))
;           ;  (.write (.-stdin repl-process) (str "(-> (sh " input ") get-out-stream (.pipe _closh_out_stream_))\n")))))
;         ;  (js/console.log "Received:" line)
;         ;  (.prompt rl)))
;   (.on "close" #(.exit js/process 0)))
;
; (.prompt rl)

; (def x '(+ 2 5))
; (def x (cljs.reader/read-string "(* 2 5)\n"))
; (js/console.log (lumo.repl/eval x))

; (def input "(+ 2 3)")
;
; (defn example-eval []
;   (-> input
;       (cljs.reader/read-string)
;       (lumo.repl/eval)
;       (pr-str)
;       (js/console.log)))
;
;
; (example-eval)
; (js/setTimeout example-eval 100)

;
;
; ; (def input '(.-name *ns*))
;
; ; (js/setTimeout
; ;   100)
;
; (js/setTimeout
;  #(.write js/process.stdout
;     (->>
;         ;  (cljs.reader/read-string)
;         ;  (lumo.repl/eval)
;         ;  [type source-or-path expression? print-nil-result? setNS session-id]
;          (lumo.repl/execute "text" input true true nil 0)
;          (JSON.stringify)))
;         ;  (js/console.log))
;         ;  (prn-str)))
;  100)


(defn -main []
  (js/console.log "Hello world"))

(set! *main-cli-fn* -main)
