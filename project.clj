(defproject memory-cljs "0.1.0-SNAPSHOT"
  :description "Memory Game"
  :url "http://example.com/FIXME"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.cemerick/url "0.1.1"]
                 [figwheel "0.2.3-SNAPSHOT"]
                 [org.clojure/clojurescript "0.0-2760" :scope "provided"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [sablono "0.2.22"]
                 [org.omcljs/om "0.8.6"]]

  :plugins [[lein-cljsbuild "1.0.4"]
            [lein-figwheel "0.2.3-SNAPSHOT"]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled"]

  :cljsbuild {
    :builds [{:id "memory-cljs"
              :source-paths ["src" "dev_src"]
              :compiler {
                :output-to "resources/public/js/compiled/memory_cljs.js"
                :output-dir "resources/public/js/compiled/out"
                :asset-path "js/compiled/out"
                :main memory-cljs.dev
                :optimizations :none
                :cache-analysis true
                :source-map true
                :source-map-timestamp true}}]}

 :figwheel {
             :http-server-root "public" ;; default and assumes "resources" 
             :server-port 3449 ;; default
             :css-dirs ["resources/public/css"] ;; watch and update CSS

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is simple ring servers, if this
             ;; doesn't work for you just run your own server :)
             ;; :ring-handler hello_world.server/handler

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log" 
             })

