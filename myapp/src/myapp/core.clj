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
    (prn (list 'do '(in-ns 'user)
               (list 'load-script f)))))

(defn read-output-stream [^java.io.BufferedReader in]
  (future
    (try
      (loop []
        (let [eof -1 ;(char 0x04)
              c-int (.read in)]
          (if (= c-int eof)
            (println "Received SIGINT")
            (let [c (char c-int)]
              ;; FIXME Something weird with the offset of the string in the terminal (not sure)
              #_(println "Received input" (pr-str c))
              (print c) (flush)
              (recur)))))
      (catch Throwable t
        (println "Failure in loop " (ex-message t))))))

(defn -main [filename & [delay] ]
  ;; Connect to closh server
  (let [s (Socket. "127.0.0.1" 49999)]
    (if (.isConnected s)
      (let [in (BufferedReader. (InputStreamReader. (.getInputStream s)))
            out (PrintWriter. (.getOutputStream s) true)]
        (let [script (.getAbsolutePath (clojure.java.io/file filename))]
          (println "file" script)
          (println (slurp script))
          (load-script out script)
          (binding [*out* out]
            (println :repl/quit)))

        (read-output-stream in)
        (if delay
          (Thread/sleep (Long/parseLong delay))) ;; Missing exceptions otherwise
        (println "Done")
        (System/exit 0)))))
