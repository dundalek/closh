(defproject closh "0.1.3"
  :description "Bash-like shell based on Clojure"
  :url "https://github.com/dundalek/closh"

  :clean-targets ["build" :target-path]

  :dependencies [[org.clojure/clojure "1.9.0-RC1"]
                 [org.clojure/clojurescript "1.9.946"]
                ;  [org.clojure/spec.alpha "0.1.143"]

                 ; Lumo dependencies
                 [com.cognitect/transit-cljs  "0.8.243"]
                 [malabarba/lazy-map          "1.3"]
                 [fipp                        "0.6.12"]]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-kibit "0.1.6"]
            [lein-bikeshed "0.5.0"]
            [venantius/yagni "0.1.4"]
            [jonase/eastwood "0.2.5"]
            [funcool/codeina "0.4.0" :exclusions [org.clojure/clojure]]]

  :jvm-opts ["--add-modules" "java.xml.bind"]

  :aliases {"build" ["cljsbuild" "once" "main"]
            "lint" ["do" ["kibit"] ["eastwood"] ["bikeshed"] ["yagni"]]}

  :codeina {:reader :clojurescript}

  ;; This release-task does lein npm publish in addition to lein deploy
  :release-tasks [["vcs" "assert-committed"]
                  ["clean"]
                  ["build"]
                  ["change" "version"
                   "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ;; Uncomment the following line to distribute via npm
                  ; ["npm" "publish"]
                  ;; The following line deploys to a maven repo
                  ["deploy"]
                  ["change" "version"
                   "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :cljsbuild {:builds [{:id "main"
                        :source-paths ["src" "/home/me/dl/git/lumo/src/cljs/snapshot"]
                        :compiler {:output-to "build/main.js"
                                   :output-dir "build/js"
                                   :optimizations :advanced
                                   :target :nodejs
                                   :source-map "build/main.js.map"}}]})
