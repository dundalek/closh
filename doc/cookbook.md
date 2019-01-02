
Take a look at example [config files](https://github.com/dundalek/dotfiles/tree/master/closh). Also get inspired by [community configs](https://github.com/search?q=in%3Apath+closhrc&type=Code).

## Running nREPL server

[Pomegranate](https://github.com/cemerick/pomegranate) is included on the classpath so you can dynamically load other libraries. Using pomegranate nREPL server can be included and started. For example you can put following into your `~/.closhrc`:

```clojure
(defn start-nrepl
  ([] (start-nrepl 7888))
  ([port]
   (eval
    `(do
       (require '[cemerick.pomegranate])
       (cemerick.pomegranate/add-dependencies
         :coordinates '[[org.clojure/tools.nrepl "0.2.13"]]
         :repositories (merge cemerick.pomegranate.aether/maven-central
                              {"clojars" "https://clojars.org/repo"}))
       (require '[clojure.tools.nrepl.server])
       (println "Starting nrepl at" ~port)
       (defonce server (clojure.tools.nrepl.server/start-server :port ~port))))))
```

Then start the nREPL server with:
```clojure
(start-nrepl)
```

Connect to it from other client, for example:
```sh
lein repl :connect 7888
```

The current nREPL support is limited, for example the custom reader is not included. It can probably be added via middleware. If you have some experience creating nREPL middleware please leave a note in [#88](https://github.com/dundalek/closh/issues/88). So shelling out via nREPL at the momemnt needs to be done with `sh` macros:

```clojure
$ (sh-str echo hello nrepl)
$ (sh-value ls *.txt)
```

## Autojump

To enable [Autojump](https://github.com/wting/autojump) refer to a following [configuration](https://github.com/dundalek/dotfiles/blob/master/closh/.closh_autojump.cljc).

## NVM integration

To use [nvm](https://github.com/creationix/nvm) put the following into your `~/.closhrc`:
```clojure
(source-shell "export NVM_DIR=\"$HOME/.nvm\"; [ -s \"$NVM_DIR/nvm.sh\" ] && . \"$NVM_DIR/nvm.sh\"")

(defn args->str [args]
  (->> args
    (map #(str "'" (clojure.string/replace % #"'" "'\"'\"'") "'"))
    (clojure.string/join " ")))

(defcmd nvm [& args]
  (print (source-shell (str ". \"$NVM_DIR/nvm.sh\"; nvm " (args->str args)))))
```

## Conda integration

To use [conda](https://anaconda.org/) put the following into your `~/.closhrc`:

```clojure
(source-shell ". ~/anaconda/etc/profile.d/conda.sh; conda activate")
```

## Using Google Closure Library

Closure library is built in, so you can use it like so:

```clojure
(require 'goog.string.format)
(goog.string.format "%03d" 7)
; => "007"
```

## Manipulating multiple files

Often there is a need to do some work with multiple files. An example might be to convert all text files in a directory to PDFs for printing. A single file can be converted with `unoconv abc.txt abc.pdf`.

To convert all txt files in a directory with `bash` you can do:
```bash
for f in *.txt; do unoconv "$f" `echo "$f" | sed 's/\.txt$/.pdf/'`; done
```

Here is an example how you could do that with `closh`:
```clojure
(doseq [f (expand "*.txt")] (sh unoconv (str f) (str/replace f #"\.txt$" ".pdf")))
```

## Scripting

The development of closh is currently focused on the interactive mode and exploring possible ideas in that area. However, I would like the script mode to be eventually supported as well. In the mean time you can write scripts in plain cljs an clj and use closh as a helper library:

```clojure
#!/bin/sh

clojure -Sdeps '{:deps {closh {:git/url "https://github.com/dundalek/closh.git" :sha "093c8a55c9c3d2a326435d6943a92e5b8090cda1"}}}' -<<END

(require '[closh.zero.macros :refer :all]
         '[closh.zero.core :refer :all]
         '[closh.zero.pipeline :refer :all])

(println "hello clojure")

(sh echo hello closh)

(println "Number of files:" (sh-val ls |> (count)))

END
```
