(ns closh.zero.frontend.main
  (:require [clojure.tools.reader.reader-types :refer [string-push-back-reader push-back-reader]]
            [closh.zero.reader :refer [read-sh]]
            [closh.zero.platform.process :refer [process?]]
            [closh.zero.env :refer [*closh-environment-init*]]
            [closh.zero.utils.clojure-main :refer [repl repl-requires] :as clojure-main])
  (:refer-clojure :exclude [load-reader])
  (:import [clojure.lang Compiler RT]
           [java.io File FileInputStream InputStreamReader StringReader]))

(def custom-environment
  (str "(alter-var-root #'load-file (constantly closh.zero.frontend.main/compiler-load-file))"
       "(def ^{:dynamic true} *args* *command-line-args*)"))

(defn repl-read
  [request-prompt request-exit]
  (read-sh {:read-cond :allow} *in*))

(defn repl-print
  [& args]
  (when-not (or (nil? (first args))
                (process? (first args)))
    (apply prn args)))

(defn repl-opt
  [[_ & args] inits]
  (repl :init (fn []
                (apply require repl-requires)
                (eval *closh-environment-init*))
        :read repl-read
        :print repl-print)
  (prn)
  (System/exit 0))

;; Reimplementation of Compiler.loadFile
(defn compiler-load-file [file]
  (let [f (FileInputStream. file)
        ;; rdr (InputStreamReader. f RT/UTF8)
        rdr (StringReader.
             (str
               custom-environment
               (pr-str *closh-environment-init*)
               (closh.zero.reader/read-transform (push-back-reader (InputStreamReader. f RT/UTF8)))))]
    (try
      (Compiler/load
        rdr
        (.getAbsolutePath (File. file))
        (.getName (File. file)))
      (finally
        (.close f)))))

;; clojure.main/load-script
(defn load-script
  "Loads Clojure source from a file or resource given its path. Paths
  beginning with @ or @/ are considered relative to classpath."
  [^String path]
  (if (.startsWith path "@")
    (RT/loadResourceScript
     (.substring path (if (.startsWith path "@/") 2 1)))
    ;; (Compiler/loadFile path)))
    (compiler-load-file path)))

;; clojure.core/load-reader
(defn load-reader
  "Sequentially read and evaluate the set of forms contained in the
  stream/file"
  {:added "1.0"
   :static true}
  [rdr]
  (let [closh-reader
        (StringReader.
             (str
               custom-environment
               (pr-str *closh-environment-init*)
               (closh.zero.reader/read-transform rdr)))]
    ;; (. clojure.lang.Compiler (load rdr)))
    (Compiler/load closh-reader)))

(def eval-opt-orig clojure-main/eval-opt)

(defn eval-opt [s]
  (eval-opt-orig
    (str
      (pr-str *closh-environment-init*)
      (closh.zero.reader/read-transform (string-push-back-reader s)))))

(defn -main [& args]
  (with-redefs [clojure-main/load-script load-script
                clojure-main/eval-opt eval-opt
                clojure-main/repl-opt repl-opt
                clojure.core/load-reader load-reader]
                ;; redef does not seem to work, must use alter var root
                ;; clojure.core/load-file compiler-load-file]
    (apply clojure-main/main args)))
