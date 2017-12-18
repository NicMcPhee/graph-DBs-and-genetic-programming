(defproject neocons_scripts "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clojurewerkz/neocons "3.1.0"]
                 [selmer "1.11.3"]]
  :main ^:skip-aot neocons-scripts.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
