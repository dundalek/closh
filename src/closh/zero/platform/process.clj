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
  (.getCanonicalPath (File. "")))

(defn chdir [dir]
  (let [target (.getAbsolutePath (File. dir))]
    (if (.isDirectory (File. target))
      (System/setProperty "user.dir" target)
      (throw (Exception. (str target ": Is not a directory"))))))

(defn setenv
  ([k] (swap! *env* dissoc k))
  ([k v] (let [val (str v)]
           (swap! *env* assoc k val)
           val)))

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
            std-flip (atom false)
            redirects
            (reduce
              (fn [redirects [op fd target]]
                (case op
                  :rw (throw (Exception. "Read/Write redirection is not supported"))
                  (let [redirect (case op
                                   :in (java.lang.ProcessBuilder$Redirect/from (File. target))
                                   :out (java.lang.ProcessBuilder$Redirect/to (File. target))
                                   :append (java.lang.ProcessBuilder$Redirect/appendTo (File. target))
                                   :set (if (#{:stdin :stdout :stderr} target)
                                          target
                                         (get redirects target)))]
                    (if redirect
                      (assoc redirects fd redirect)
                      redirects))))
              {0 :stdin 1 :stdout 2 :stderr}
              (:redir opts))]
           (doseq [[fd target] redirects]
             (let [redir (if (#{:stdin :stdout :stderr} target)
                           java.lang.ProcessBuilder$Redirect/PIPE
                           target)]
               (case fd
                 0 (.redirectInput builder redir)
                 1 (if (= target :stderr)
                     (reset! std-flip true)
                     (.redirectOutput builder redir))
                 2 (if (= target :stdout)
                     (.redirectErrorStream builder true)
                     (.redirectError builder redir))
                 (throw (Exception. (str "Unsupported file descriptor: " fd " (only file descriptors 0, 1, 2 are supported)"))))))
        (let [env (.environment builder)]
          (.clear env)
          (doseq [[k v] (getenv)]
            (.put env k v)))
        (.directory builder (File. (cwd)))
        (let [process (.start builder)]
          (if @std-flip
            (proxy [java.lang.Process] []
              (destroy []
                (.destroy process))
              (destroyForcibly []
                (.destroyForcibly process))
              (exitValue []
                (.exitValue process))
              (getErrorStream []
                (.getInputStream process))
              (getInputStream []
                (.getErrorStream process))
              (getOutputStream []
                (.getOutputStream process))
              (isAlive []
                (.isAlive process))
              (waitFor
                ([] (.waitFor process))
                ([timeout unit] (.waitFor process timeout unit))))
            process)))))
