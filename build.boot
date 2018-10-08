#!/usr/bin/env boot

(set-env! :dependencies '[
[org.clojure/clojure "1.9.0"]
[seancorfield/boot-tools-deps "0.4.6" :scope "test"]

(require '[boot-tools-deps.core :refer [deps]])

(deftask uberjar
  "Builds an uberjar of this project that can be run with java -jar"
  []
  (comp
   (deps :quick-merge true)
   (aot :all true)
   (uber)
   (jar :file "project.jar" :main 'closh.zero.frontend.main)
   (sift :include #{#"project.jar"})
   (target)))
