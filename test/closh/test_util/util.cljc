(ns closh.test-util.util)

(def null-file
  (if
    #?(:cljs (= js/process.platform "win32")
       :clj (-> (System/getProperty "os.name")
                (.toLowerCase)
                (.indexOf "win")
                (pos?)))
    "nul"
    "/dev/null"))
