
## Autojump

To enable [Autojump](https://github.com/wting/autojump) refer to following [.closhrc](https://github.com/dundalek/dotfiles/blob/master/closh/.closh.cljs#L56) configuration.

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

```clj
(source-shell ". ~/anaconda/etc/profile.d/conda.sh; conda activate")
```

## Using Google Closure Library

Closure library is built in, so you can use it like so:

```clj
(require 'goog.string.format)
(goog.string.format "%03d" 7)
; => "007"
```

## Scripting

The development of closh is currently focused on the interactive mode and exploring possible ideas in that area. However, I would like the script mode to be eventually supported as well. In the mean time you can write scripts in plain cljs an clj and use closh as a helper library:

```
#!/bin/sh

clojure -Sdeps '{:deps {closh {:git/url "https://github.com/dundalek/closh.git" :sha "f6416f81bf132a037fcf437ed07dc09adb4c14b7"}}}' - <<END

(require '[closh.zero.macros :refer :all]
         '[closh.zero.core :refer :all]
         '[closh.zero.pipeline :refer :all])

(println "hello clojure")

(sh echo hello closh)

(println "Number of files:" (sh-val ls |> (count)))

END
```
