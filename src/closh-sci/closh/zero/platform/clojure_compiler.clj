(ns closh.zero.platform.clojure-compiler
  (:refer-clojure :exclude [load load-file eval])
  (:require [closh.zero.reader :as reader]
            [closh.zero.parser]
            [closh.zero.compiler]
            #_[closh.zero.platform.eval :as eval]
            [clojure.tools.reader.reader-types :as r])
  (:import (clojure.lang Compiler RT LineNumberingPushbackReader LispReader$ReaderException Compiler$CompilerException)
           (java.io File FileInputStream InputStreamReader StringReader PipedWriter PipedReader PushbackReader BufferedReader)))

(defn reader-opts [source-name]
  #_(when (str/ends-with source-name ".cljc")
      RT.mapUniqueKeys (LispReader.OPT_READ_COND, LispReader.COND_ALLOW)))

;; Reimplementation of Compiler.load
(defn load
  ([rdr] (load rdr nil "NO_SOURCE_FILE"))
  ([rdr source-path source-name eval]
   (let [eof (Object.)
         rdr (r/indexing-push-back-reader rdr)
         ;;rdr ^LineNumberingPushbackReader (if (instance? LineNumberingPushbackReader rdr) rdr (LineNumberingPushbackReader. rdr))]
         ;;Object readerOpts = readerOpts(sourceName);
         ;; consumeWhitespaces(pushbackReader)
         ;; Var.pushThreadBindings(
         LINE_BEFORE (atom (r/get-line-number rdr))
         COLUMN_BEFORE (atom (r/get-column-number rdr))
         LINE_AFTER (atom (r/get-line-number rdr))
         COLUMN_AFTER (atom (r/get-column-number rdr))]
     (try
       (loop [ret nil]
         ;; TODO skip whitespace?
         (let [r (reader/read rdr false eof)]
           (if (identical? r eof)
             ret
             (do
               (reset! LINE_AFTER (r/get-line-number rdr))
               (reset! COLUMN_AFTER (r/get-column-number rdr))
               (let [ret (eval r)]
                 (reset! LINE_BEFORE (r/get-line-number rdr))
                 (reset! COLUMN_BEFORE (r/get-column-number rdr))
                 (recur ret))))))
       ;; TODO exceptions will be different
       (catch clojure.lang.ExceptionInfo e
         (let [{:keys [line row col type]} (ex-data e)]
           (case type
             :reader-exception (throw (Compiler$CompilerException. source-path line col nil Compiler$CompilerException/PHASE_READ (.getCause e)))
             :edamame/error (throw (Compiler$CompilerException. source-path row col nil Compiler$CompilerException/PHASE_READ (.getCause e)))
             :sci/error (throw (Compiler$CompilerException. source-path @LINE_BEFORE @COLUMN_BEFORE e))
             (throw e))))
       #_(catch LispReader$ReaderException e
           (throw (Compiler$CompilerException. source-path (.-line e) (.-column e) nil Compiler$CompilerException/PHASE_READ (.getCause e))))
       (catch Throwable e
         (if-not (instance? Compiler$CompilerException e)
           ;; TODO (Integer) LINE_BEFORE.deref(), (Integer) COLUMN_BEFORE.deref()
           (throw (Compiler$CompilerException. source-path @LINE_BEFORE @COLUMN_BEFORE e))
           (throw e)))))))
      ; (finally)
        ; Var.popThreadBindings())));

;; Reimplementation of Compiler.loadFile
(defn load-file [^String file eval]
  (let [f ^FileInputStream (FileInputStream. file)
        rdr ^InputStreamReader (InputStreamReader. f "UTF-8")]
        ;; rdr (make-custom-reader (PushbackReader. (InputStreamReader. f RT/UTF8)))]
    (try
      (load
       rdr
       (.getAbsolutePath (File. file))
       (.getName (File. file))
       eval)
      (finally
        (.close f)))))
