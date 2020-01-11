(ns closh.zero.frontend.clojure-compiler
  (:refer-clojure :exclude [load load-file])
  (:import (clojure.lang Compiler RT LineNumberingPushbackReader)
           (java.io File FileInputStream InputStreamReader StringReader PipedWriter PipedReader PushbackReader BufferedReader)))

(defn load [rdr source-path source-name]
  (println "stubbed compiler load" source-path source-name)
  #_(Compiler/load rdr source-path source-name))

;; Reimplementation of Compiler.loadFile
(defn load-file [file]
  (let [f (FileInputStream. file)
        rdr (InputStreamReader. f RT/UTF8)]
        ;; rdr (make-custom-reader (PushbackReader. (InputStreamReader. f RT/UTF8)))]
    (try
      (load
       rdr
       (.getAbsolutePath (File. file))
       (.getName (File. file)))
      (finally
        (.close f)))))
