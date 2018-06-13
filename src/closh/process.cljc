(ns closh.process)

(defn exit [code]
  #?(:cljs (js/process.exit code)
     :clj (System/exit code)))
