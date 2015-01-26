(defproject memory-cljs "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2725"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.omcljs/om "0.8.2"]]

  :plugins [[lein-cljsbuild "1.0.4"]]

  :source-paths ["src" "target/classes"]

  :clean-targets ["out/memory_cljs" "out/memory_cljs.js"]

  :cljsbuild {
    :builds [{:id "memory-cljs"
              :source-paths ["src"]
              :compiler {
                :output-to "out/memory_cljs.js"
                :output-dir "out"
                :optimizations :none
                :cache-analysis true
                :source-map true}}]})
