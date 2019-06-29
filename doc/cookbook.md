
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

## Direnv

If you are using [direnv](https://github.com/direnv/direnv) to switch an environment based on a working directory you can augment the `closh-prompt` definition in the `~/.closhrc` like this:

```clojure
(defn closh-prompt []
  (source-shell "bash" "eval \"$(direnv export bash)\"")
  ; your prompt logic here
  )
```

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

## AWS CLI

[cljaws](https://github.com/timotheosh/cljaws) is a project that integrates with closh and allows you to run AWS API commands with pure Clojure from the command line.

## Conda integration

To use [conda](https://anaconda.org/) put the following into your `~/.closhrc`:

```clojure
(source-shell ". ~/anaconda/etc/profile.d/conda.sh; conda activate")
```

## Integration with text editors

[Liquid](https://github.com/mogenslund/liquid) is a text editor written in Clojure inspired by Emacs and Vim. There is a [plugin](https://github.com/mogenslund/closhapp) that integrates Closh with Liquid. One cool feature is that command output is written into a text buffer and can be later edited and manipulated within the text editor.

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

## Temporarily change Current Working Directory

In bash it is usually done using subshell or directory stack:
```bash
# Using subshell
(cd SOME_PATH && exec_some_command)

# Using directory stack
pushd SOME_PATH
exec_some_command
popd
```

Possible solution in closh with a macro:
```clojure
(defmacro with-cwd [dir & body])
  `(binding [closh.zero.platform.process/*cwd*
             (atom (closh.zero.platform.process/resolve-path dir))])
    (sh ~@body)
```

Then it can be used as:
```clojure
(with-cwd "src"
  pwd \;
  ls -l)
```
