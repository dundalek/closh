(defproject closh-sci "0.0.1-SNAPSHOT"
  :description "Closh with SCI"
  :source-paths ["src/jvm" "src/common" "resources"]
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [borkdude/sci "0.0.10"]
                 [org.clojure/tools.reader "1.3.2"]
                 [squarepeg "0.6.1"]
                 [org.clojure/java.jdbc "0.7.9"]
                 [com.bhauman/rebel-readline "0.1.4"]
                 [org.jline/jline-reader "3.5.1"]]
  :main closh.zero.frontend.sci
  :profiles {:uberjar {:global-vars {*assert* false}
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"
                                  "-Dclojure.spec.skip-macros=true"]
                       :aot :all}})
