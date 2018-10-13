(ns myapp.core
  (:gen-class)
  (:import [java.io InputStreamReader]))

;; GraalVM doesn't like reflections
(set! *warn-on-reflection* true)

(defn runtime-exec [cmd-args]
(-> (.exec (Runtime/getRuntime) #^"[Ljava.lang.String;" (into-array String cmd-args))
    (.waitFor)))

(defn with-raw-tty [body-fn]
  ;; https://stackoverflow.com/questions/1066318/how-to-read-a-single-char-from-the-console-in-java-as-the-user-types-it/6876253#6876253
  (runtime-exec ["/bin/sh", "-c", "stty raw </dev/tty"])
  (try
    (body-fn)
    (finally
      ;; Above has to be reverted as well (use seperate terminals! or weird things happen!)
      (runtime-exec ["/bin/sh", "-c", "stty cooked </dev/tty"]))))

(defn connect-to-closh [input-stream _output-stream]
  ;; TODO This should be replaced with connection code to a closh backend
  (let [prompt (fn []
                 (print "$ ")
                 (flush))]
    (let [reader (InputStreamReader. input-stream)
          eof (char 0x04)]
      (prompt)
      (loop []
        (let [c (char (.read reader))]
          (if (= c eof)
            (println "Received SIGINT")
            (do
              ;; FIXME Something weird with the offset of the string in the terminal (not sure)
              (println "Received input" (pr-str c))
              (prompt)
              (recur))))))))

(defn -main
  "Thin client"
  [& args]
  (with-raw-tty
    (fn []
      (println "Opening connection")
      (connect-to-closh System/in System/out)
      (println "Connection closed"))))
