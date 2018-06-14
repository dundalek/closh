(ns closh.zero.platform.io)

#?(:cljs
   (def ^:no-doc glob-js (.-sync (js/require "glob"))))

#?(:cljs
   (defn glob [s]
     (seq (glob-js s #js{:nonull true}))))
