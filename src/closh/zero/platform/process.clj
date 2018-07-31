(ns closh.zero.platform.process
  (:import java.io.File))

(def ^:dynamic *env* (atom {}))

(defn process? [proc]
  (instance? Process proc))

(defn exit-code [proc]
  (.exitValue proc))

(defn wait
  "Wait untils process exits and all of its stdio streams are closed."
  [proc]
  (.waitFor proc)
  proc)

(defn exit [code]
  (System/exit code))

; Might not be right be should do for now
; https://stackoverflow.com/questions/1234795/why-is-the-user-dir-system-property-working-in-java
(defn cwd []
  (.getAbsolutePath (File. "")))

(defn chdir [dir]
  (System/setProperty "user.dir" (.getAbsolutePath (File. dir))))

(defn setenv
  ([k] (swap! *env* dissoc k))
  ([k v] (do
           (swap! *env* assoc k v)
           v)))

(defn getenv
  ([] (merge
       (into {} (System/getenv))
       @*env*))
  ([k] (if (contains? @*env* k)
         (get @*env* k)
         (System/getenv k))))

(defn shx
     "Executes a command as child process."
     ([cmd] (shx cmd []))
     ([cmd args] (shx cmd args {}))
     ([cmd args opts]
      (let [builder (ProcessBuilder. (into-array String (map str (concat [cmd] (flatten args)))))
            redirects
            (reduce
              (fn [redirects [op fd target]]
                (case op
                  :rw (throw (Exception. "Read/Write redirection is not supported"))
                  (let [redirect (case op
                                   :in (java.lang.ProcessBuilder$Redirect/from (File. target))
                                   :out (java.lang.ProcessBuilder$Redirect/to (File. target))
                                   :append (java.lang.ProcessBuilder$Redirect/appendTo (File. target))
                                   :set (get redirects target))]
                    (if redirect
                      (assoc redirects fd redirect)
                      redirects))))
              {}
              (:redir opts))]
           (doseq [[fd target] redirects]
             (case fd
               0 (.redirectInput builder target)
               1 (.redirectOutput builder target)
               2 (.redirectError builder target)
               (throw (Exception. (str "Unsupported file descriptor: " fd " (only file descriptors 0, 1, 2 are supported)")))))
        (let [env (.environment builder)]
          (.clear env)
          (doseq [[k v] (getenv)]
            (.put env k v)))
        (.start builder))))
