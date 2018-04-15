
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
