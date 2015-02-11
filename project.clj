(defproject memory-cljs "0.1.0-SNAPSHOT"
  :description "Memory Game"
  :url "http://example.com/FIXME"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.cemerick/url "0.1.1"]
                 [org.clojure/clojurescript "0.0-2755" :scope "provided"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.omcljs/om "0.8.8"]]

  :plugins [[lein-cljsbuild "1.0.4"]]

  :source-paths ["src" "target/classes"]

  :clean-targets ["out/memory_cljs" "memory_cljs.js"]

  :cljsbuild {
    :builds [{:id "memory-cljs"
              :source-paths ["src"]
              :compiler {
                :output-to "memory_cljs.js"
                :output-dir "out"
                :optimizations :none
                :cache-analysis true
                :source-map true}}]})
