(ns myapp.core
  (:gen-class)
  (:import [java.io
            InputStreamReader
            BufferedReader
            PrintWriter]
           [java.net Socket]))

;; GraalVM doesn't like reflections
(set! *warn-on-reflection* true)

(defn load-script [out f]
  (binding [*out* out]
    (prn (list 'do
               '(in-ns 'user)
               (list 'load-script f)))))

(require 'clojure.edn)

(defn handle-prepl-line [line]
  (let [{:keys [tag]
         value :val
         :as prepl-data} (clojure.edn/read-string line)]
    (case tag
      :out (do
             (print value)
             (flush))

      :ret (when (:exception prepl-data)
             (let [exception-data (clojure.edn/read-string value)
                   {:keys [exit-code
                           error-message]} (:data exception-data)]
               ;; We have limited printing facilities due to GraalVM, delegate to server process
               (when error-message
                 (println error-message))
               (System/exit (or exit-code 1))))

      ;; Nothing?
      nil)))

(defn read-output-stream [^java.io.BufferedReader in]
  (try
    (loop []
      (when-let [line (.readLine in)]
        (handle-prepl-line line)

        (recur)))
    (System/exit 0)
    (catch Throwable t
      (println "Failure in loop " (ex-message t)))))

(defn -main [filename]
  ;; Connect to closh server
  (let [s (Socket. "127.0.0.1" 49999)]
    (try
      (if (.isConnected s)
        (let [in (BufferedReader. (InputStreamReader. (.getInputStream s)))
              out (PrintWriter. (.getOutputStream s) true)]
          (let [script (.getAbsolutePath (clojure.java.io/file filename))]
            (load-script out script)
            (binding [*out* out]
              (println :repl/quit)))

          (read-output-stream in)))
      (finally
        (.close s)))))
