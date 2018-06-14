(ns closh.process)

(defn exit [code]
  #?(:cljs (js/process.exit code)
     :clj (System/exit code)))

; Might not be right be should do for now
; https://stackoverflow.com/questions/1234795/why-is-the-user-dir-system-property-working-in-java
(defn cwd []
  #?(:cljs (js/process.cwd)
     :clj (.getAbsolutePath (File. ""))))

(defn chdir [dir]
  #?(:cljs (js/process.chdir dir)
     :clj (System/setProperty "user.dir" (.getAbsolutePath (File. dir)))))
