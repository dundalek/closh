(ns closh.zero.platform.process
  (:import java.io.File))

(def ^:dynamic *env* (atom {}))

(defn process? [proc]
  (throw (Exception. "Not Implemented")))

(defn wait
  "Wait untils process exits and all of its stdio streams are closed."
  [proc]
  (throw (Exception. "Not Implemented")))

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
   (case fd
     0 (.redirectInput builder)
     1 (.redirectOutput builder)
     2 (.redirectError builder)
     (throw (Exception. (str "Unsupported file descriptor: " fd " (only file descriptors 0, 1, 2 are supported)")))))
  ([builder fd target]
   (case fd
     0 (.redirectInput builder target)
     1 (.redirectOutput builder target)
     2 (.redirectError builder target)
     (throw (Exception. (str "Unsupported file descriptor: " fd " (only file descriptors 0, 1, 2 are supported)"))))))

; TODO pass environment variables from *env*
(defn shx
     "Executes a command as child process."
     ([cmd] (shx cmd []))
     ([cmd args] (shx cmd args {}))
     ([cmd args opts]
      (let [builder (ProcessBuilder. (into-array String (map str (concat [cmd] args))))]
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
        (let [process (.start builder)]
          {:out (.getInputStream process)
           :in  (.getOutputStream process)
           :err (.getErrorStream process)
           :process process}))))

(defn setenv [k v]
  (swap! *env* assoc k v)
  v)

(defn getenv
  ([] (merge
       (into {} (System/getenv))
       @*env*))
  ([k] (if (contains? @*env* k)
         (get @*env* k)
         (System/getenv k))))
