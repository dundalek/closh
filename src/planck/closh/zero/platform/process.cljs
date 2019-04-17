(ns closh.zero.platform.process
  (:require ;;[closh.zero.platform.io :refer [open-io-streams]]
            [planck.shell :refer [sh *sh-dir* *sh-env*]]
            [planck.core]))

(defn process? [proc]
  (= #{:exit :out :err} (set (keys proc))))

(defn exit-code [proc]
  (:exit proc))

(defn wait
  "Wait untils process exits and all of its stdio streams are closed."
  [proc]
  ; (when (and (process? proc)
  ;            (nil? (exit-code proc)))
  ;   (wait-for-event proc "close"))
  proc)

(defn exit [code]
  (planck.core/exit code))

(defn cwd []
  *sh-dir*)

(defn chdir [dir]
  ;; todo maybe need to resolve dir
  (set! *sh-dir* dir))

(defn shx
  "Executes a command as child process."
  ([cmd] (shx cmd []))
  ([cmd args] (shx cmd args {}))
  ([cmd args opts]
   (apply sh (concat cmd (flatten args)))))
   ;;#js{:stdio (open-io-streams (:redir opts))})))

(defn setenv
  ([k] (set! *sh-env* (dissoc *sh-env* k)))
  ([k v] (do (set! *sh-env* (assoc *sh-env* k v))
             v)))

(defn getenv
  ([] *sh-env*)
  ([k] (get *sh-env* k)))
