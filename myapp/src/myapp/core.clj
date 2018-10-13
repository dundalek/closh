(ns myapp.core
  (:gen-class)
  (:import [java.io InputStreamReader]))


;; Connect to server (forward stdin and out)

;;
;; GraalVM doesn't like reflections
(set! *warn-on-reflection true)

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

(defn io-loop []
  (println "Opening connection")
  (print "$ ")
  (flush)
  (let [reader (InputStreamReader. System/in)
        eof (char 0x04)] ;;
    ;; Connect here
    (loop []
      (let [c (char (.read reader))]
        (if (= c eof)
          (println "Received SIGINT")
          (do
            (print c)
            (flush)
            ;; Forward character to socket

            ;; Repeat
            (recur)))))))

(defn -main
  "Thin client"
  [& args]
  (with-raw-tty io-loop))
