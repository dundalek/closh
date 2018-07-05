(ns closh.zero.platform.process)

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
