(ns closh.main
  (:require [clojure.tools.reader]
            [clojure.tools.reader.impl.commons]
            [closh.eval :refer [eval-cljs]]
            [closh.core :refer [get-out-stream wait-for-process wait-for-event handle-line]])
  (:require-macros [alter-cljs.core :refer [alter-var-root]]))

(def parse-symbol-orig clojure.tools.reader.impl.commons/parse-symbol)

(defn parse-symbol [token]
  (let [parts (.split token "/")
        symbols (map (comp second parse-symbol-orig) parts)
        pairs (->> (interleave parts symbols)
                   (partition 2))]
    (if (every? #(or (second %) (empty? (first %))) pairs)
      [nil (clojure.string/join "/" symbols)]
      parse-symbol-orig)))

; Hack reader to accept symbols with multiple slashes
(alter-var-root (var clojure.tools.reader.impl.commons/parse-symbol)
                (constantly parse-symbol))

; (def spawn (.-spawn (js/require "child_process")))

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

; (eval '(defn myfunc [a b] (+ a b)))
; (eval '(cljs.core$macros/defn myfunc [a b] (+ a b)))
; (js/console.log "x" (eval '(myfunc 3 4)))

(def readline (js/require "readline"))

(defn -main []
  (let [rl (.createInterface readline
             #js{:input js/process.stdin
                 :output js/process.stdout
                 :prompt "$ "})]
    (-> rl
      (.on "line" #(do (handle-line % eval-cljs)
                       (.prompt rl)))
      (.on "close" #(.exit js/process 0)))
    (.prompt rl)))

(set! *main-cli-fn* -main)
