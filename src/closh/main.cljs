(ns closh.main
  (:require [clojure.tools.reader]
            [clojure.tools.reader.impl.commons]
            [lumo.repl]
            [cljs.env :as env]
            [cljs.js :as cljs]
            [closh.parser :refer [parse]]
            [closh.core :refer [shx expand pipe]]))
  ; (:require-macros [alter-cljs.core :refer [alter-var-root]]))

; (def parse-symbol-orig clojure.tools.reader.impl.commons/parse-symbol)
;
; (defn parse-symbol [token]
;   (let [parts (.split token "/")
;         symbols (map (comp second parse-symbol-orig) parts)
;         pairs (->> (interleave parts symbols)
;                    (partition 2))]
;     (if (every? #(or (second %) (empty? (first %))) pairs)
;       [nil (clojure.string/join "/" symbols)]
;       parse-symbol-orig)))
;
; ; Hack reader to accept symbols with multiple slashes
; (alter-var-root (var clojure.tools.reader.impl.commons/parse-symbol)
;                 (constantly parse-symbol))



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

(def ns *ns*)
(def compiler env/*compiler*)
(def eval-fn cljs/*eval-fn*)
(def load-fn cljs/*load-fn*)

(defn eval [form]
  (lumo.repl/eval form ns compiler))

(eval '(require '[closh.core :refer [shx expand pipe process-output]]
                '[clojure.string :as str]))

(defn read-command [input]
  (if (re-find #"^\s*#?\(" input)
    (clojure.tools.reader/read-string input)
    (-> (str "(" input ")")
        (clojure.tools.reader/read-string)
        (parse))))

(defn handle-line [input]
  (binding [cljs/*eval-fn* eval-fn
            cljs/*load-fn* load-fn]
      (let [result (-> input
                     (read-command)
                     (lumo.repl/eval ns compiler))]
        (.pipe (.-stdout result) js/process.stdout)
        (.write js/process.stdout (prn-str result)))))

(defn -main []
  (let [rl (.createInterface readline
             #js{:input js/process.stdin
                 :output js/process.stdout
                 :prompt "$ "})]
    (-> rl
      (.on "line" #(do (handle-line %)
                       (.prompt rl)))
      (.on "close" #(.exit js/process 0)))
    (.prompt rl)))

(set! *main-cli-fn* -main)
