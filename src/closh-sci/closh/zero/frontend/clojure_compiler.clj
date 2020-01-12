(ns closh.zero.frontend.clojure-compiler
  (:refer-clojure :exclude [load load-file eval])
  (:require [closh.zero.reader :as reader]
            [closh.zero.parser]
            [closh.zero.compiler]
            [closh.zero.platform.eval :as eval])
  (:import (clojure.lang Compiler RT LineNumberingPushbackReader LispReader$ReaderException Compiler$CompilerException)
           (java.io File FileInputStream InputStreamReader StringReader PipedWriter PipedReader PushbackReader BufferedReader)))

(defn eval [form]
  (eval/eval
    (closh.zero.compiler/compile-interactive
      (closh.zero.parser/parse form))))

(defn reader-opts [source-name]
  #_(when (str/ends-with source-name ".cljc")
      RT.mapUniqueKeys(LispReader.OPT_READ_COND, LispReader.COND_ALLOW)))

(defn load [rdr source-path source-name]
  (let [eof (Object.)
        rdr ^LineNumberingPushbackReader (if (instance? LineNumberingPushbackReader rdr) rdr (LineNumberingPushbackReader. rdr))]
        ;;Object readerOpts = readerOpts(sourceName);
    ;; consumeWhitespaces(pushbackReader)
    ;; Var.pushThreadBindings(
    (try
      (loop [ret nil]
        (let [r (reader/read-compat rdr false eof)]
          (if (identical? r eof)
            ret
            (do
              ; LINE_AFTER.set(pushbackReader.getLineNumber());
              ; COLUMN_AFTER.set(pushbackReader.getColumnNumber());
              (recur (eval r))))))
              ; LINE_BEFORE.set(pushbackReader.getLineNumber());
              ; COLUMN_BEFORE.set(pushbackReader.getColumnNumber()))));
      ;; TODO exceptions will be different
      (catch LispReader$ReaderException e
        (throw (Compiler$CompilerException. source-path (.-line e) (.-column e) nil Compiler$CompilerException/PHASE_READ (.getCause e))))
      (catch Throwable e
        (if-not (instance? Compiler$CompilerException e)
          ;; TODO (Integer) LINE_BEFORE.deref(), (Integer) COLUMN_BEFORE.deref()
          (throw (Compiler$CompilerException. source-path 0 0 e))
          (throw e)))))
      ; (finally)
        ; Var.popThreadBindings())));
  #_(println "stubbed compiler load" source-path source-name)
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
