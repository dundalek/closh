(ns myapp.closhrc-example)

(def HOME (getenv "HOME"))

(defcmd closhrc []
  (load-file (str HOME "/.closhrc")))

(defcmd ecloshrc []
  (sh "mate" (str HOME "/.closhrc")))

;; Lib

(defn add-dependencies
  "A helper function to lazily load dependencies using Pomegranate."
  [& args]
  (when-not (find-ns 'cemerick.pomegranate)
    (require '[cemerick.pomegranate]))
  (apply (resolve 'cemerick.pomegranate/add-dependencies)
    (concat args
      [:repositories (merge @(resolve 'cemerick.pomegranate.aether/maven-central) {"clojars" "https://clojars.org/repo"})])))

;; / Lib

(defn current-pid []
  (when-not (find-ns 'clj-pid.core)
    (add-dependencies :coordinates '[[clj-pid "0.1.2"]])
    (require '[clj-pid.core :as pid]))

  ((resolve 'pid/current)))

;; /var/run/closh-script-server.pid (Permission denied)
(def pid-file (str HOME "/.closh-script-server.pid") #_"/var/run/closh-script-server.pid")

(defn closh-script-server-pid []
  (slurp pid-file))

(defn kill-closh-script-server-pid []
  (sh "kill" "-9" (slurp pid-file)))

(defn clear-closh-script-server-pid []
  (let [f (clojure.java.io/file pid-file)]
    (when (.exists f)
      (.delete f))))

(defn start-script-server []
  (let [f (clojure.java.io/file pid-file)]
    (when-not (.exists f)
      (spit f (current-pid))
      ((resolve 'pid/delete-on-shutdown!) pid-file)
      (clojure.core.server/start-server {:port 49999 :name "closh-server" :accept 'clojure.core.server/io-prepl}))))

(defn start-script-server! []
  (clear-closh-script-server-pid)
  (start-script-server))

(defn reload-script-server []
  (kill-closh-script-server-pid)
  (start-script-server!)
  (closhrc))


(require 'clojure.datafy
         'clojure.pprint)

(defn with-temporary-ns [filename f]

  (let [prev-ns (.getName *ns*)
        temp-ns-sym (symbol (str filename))
        temp-ns (create-ns temp-ns-sym)]
    (try
      (in-ns temp-ns-sym)
      (require '[clojure.core :refer :all])
      ;; REVIEW Not like this
      ; (require [prev-ns :refer :all])
      (f)
      (finally
        (in-ns prev-ns)
        (remove-ns temp-ns-sym)))))

(defn throw-execution-error [ex]
  (throw (ex-info "Execution error (see data)"
                  {:exception ex
                   :error-message (with-out-str
                                    (println)
                                    (println "Exception:")
                                    (clojure.pprint/pprint ex))
                   :exit-code 1})))

(defn load-script [f]
  (let [old-security (System/getSecurityManager)
        custom-security (proxy [java.lang.SecurityManager] []
                          (checkExit [status]
                            (throw (SecurityException. "See cause"
                                                       (ex-info "status wrapper" {::exit-code status}))))
                          (checkPermission
                            ([_])
                            ([_ _])))]
    (System/setSecurityManager custom-security)

    (try
      (with-temporary-ns f #(load-file f))

      (catch Throwable e
        (let [ex (clojure.datafy/datafy e)]
          (if-let [status (get-in ex [:data ::exit-code])]
            (throw (ex-info "No real exception: System exit call "
                            {:exception ex
                             :exit-code status}))
            (throw-execution-error ex))))
      (finally
        (when old-security
          (System/setSecurityManager old-security))))))

(start-script-server)

(println "Closhrc loaded")
