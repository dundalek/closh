(defproject closh "0.2.0"
  :description "Bash-like shell based on Clojure"
  :url "https://github.com/dundalek/closh"

  :dependencies [[org.clojure/clojure "1.9.0-RC1"]
                 [org.clojure/clojurescript "1.9.946"]]
                ;  [org.clojure/spec.alpha "0.1.143"]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-kibit "0.1.6"]
            [lein-bikeshed "0.5.0"]
            [venantius/yagni "0.1.4"]
            [jonase/eastwood "0.2.5"]
            [funcool/codeina "0.4.0" :exclusions [org.clojure/clojure]]]

  :aliases {"lint" ["do" ["kibit"] ["eastwood"] ["bikeshed"] ["yagni"]]}

  :source-paths ["src"]
  :test-paths ["test"]

  ; runs with `lein doc`
  :codeina {:reader :clojurescript})
