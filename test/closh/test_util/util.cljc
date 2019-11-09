(ns closh.test-util.util
  #?(:cljs (:require [fs]
                     [tmp]
                     [closh.zero.platform.util :refer [wait-for-event]])))

;; Clean up tmp files on unhandled exception
#?(:cljs (tmp/setGracefulCleanup))

(defn with-tempfile [cb]
  #?(:cljs
     (let [file (tmp/fileSync)
           f (.-name file)
           result (cb f)]
       (.removeCallback file)
       result)
     :clj
     (let [file (java.io.File/createTempFile "closh-test-" ".txt")
           f (.getAbsolutePath file)
           _ (.deleteOnExit file)]
       (cb f))))

(defn with-tempfile-content [cb]
  (with-tempfile
    (fn [f]
      (cb f)
      #?(:cljs (fs/readFileSync f "utf-8")
         :clj (slurp f)))))

(def null-file
  (if
   #?(:cljs (= js/process.platform "win32")
      :clj (-> (System/getProperty "os.name")
               (.toLowerCase)
               (.indexOf "win")
               (pos?)))
    "nul"
    "/dev/null"))

(defn create-fake-writer []
  #?(:clj (java.io.ByteArrayOutputStream.)
     :cljs
     (let [file (tmp/fileSync)
           name (.-name file)
           stream (fs/createWriteStream name)]
       (wait-for-event stream "open")
       {:file file
        :name name
        :stream stream})))

(defn get-fake-writer [writer]
  #?(:clj (java.io.PrintStream. writer)
     :cljs (:stream writer)))

(defn str-fake-writer [writer]
  #?(:clj (str writer)
     :cljs (let [content (fs/readFileSync (:name writer) "utf-8")]
             (.removeCallback (:file writer))
             content)))

(defmacro with-async [& body]
  `(clojure.test/async done#
                       (->
                        ~@body
                        (.catch (fn [err#] (clojure.test/is (nil? err#))))
                        (.then done#))))
