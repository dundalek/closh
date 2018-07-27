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

(defn builder-redirect
  ([builder fd]
   (condp contains? fd
     #{0 :stdin} (.redirectInput builder)
     #{1 :stdout} (.redirectOutput builder)
     #{2 :stderr} (.redirectError builder)
     (throw (Exception. (str "Unsupported file descriptor: " fd " (only file descriptors 0, 1, 2 are supported)")))))
  ([builder fd target]
   (condp contains? fd
     #{0 :stdin} (.redirectInput builder target)
     #{1 :stdout} (.redirectOutput builder target)
     #{2 :stderr} (.redirectError builder target)
     (throw (Exception. (str "Unsupported file descriptor: " fd " (only file descriptors 0, 1, 2 are supported)"))))))

; TODO pass environment variables from *env*
(defn shx
     "Executes a command as child process."
     ([cmd] (shx cmd []))
     ([cmd args] (shx cmd args {}))
     ([cmd args opts]
      (let [builder (ProcessBuilder. (into-array String (map str (concat [cmd] (flatten args)))))]
        (when (:redir opts)
          (doseq [[op fd target] (:redir opts)]
            (case op
              :rw (throw (Exception. "Read/Write redirection is not supported"))
              (let [redirect (case op
                               :in (java.lang.ProcessBuilder$Redirect/from (File. target))
                               :out (java.lang.ProcessBuilder$Redirect/to (File. target))
                               :append (java.lang.ProcessBuilder$Redirect/appendTo (File. target))
                               :set (builder-redirect builder target))]
                (builder-redirect builder fd redirect)))))
        (.start builder))))

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
