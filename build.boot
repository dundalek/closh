#!/usr/bin/env boot

(set-env!
 :resource-paths #{"resources"}
 :dependencies
 '[[org.clojure/clojure "1.10.1"]
   [seancorfield/boot-tools-deps "0.4.6" #_:scope #_"provided"]])

(require '[boot-tools-deps.core :refer [deps]])

(deftask uberjar
  "Builds an uberjar of this project that can be run with java -jar"
  []
  (comp
   (deps :quick-merge true)
   (aot :all true)
   (uber)
   (jar :file "closh-zero.jar" :main 'closh.zero.frontend.rebel)
   (sift :include #{#"closh-zero.jar"})
   (target)))

(deftask uberjar-sci
  "Builds an uberjar of this project that can be run with java -jar"
  []
  (comp
   (deps :quick-merge true :aliases [:sci])
   (aot :all true)
   (uber)
   (jar :file "closh-zero-sci.jar" :main 'closh.zero.frontend.sci)
   (sift :include #{#"closh-zero-sci.jar"})
   (target)))
