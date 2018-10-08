#!/usr/bin/env boot

(set-env! :dependencies '[[seancorfield/boot-tools-deps "0.4.6" :scope "test"]])

(require '[boot-tools-deps.core :refer [deps]])

(deftask uberjar
  "Builds an uberjar of this project that can be run with java -jar"
  []
  (comp
   (deps :overwrite-boot-deps true)
   (aot :namespace #{'closh.zero.frontend.main})
   (uber)
   (jar :file "project.jar" :main 'closh.zero.frontend.main)
   (sift :include #{#"project.jar"})
   (target)))
