(defproject solar "0.1.0-SNAPSHOT"
  :description "Solar test exercise"
  :url ""
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.3.441"]
                 [org.clojure/tools.logging "0.3.1"]
                 [http-kit "2.2.0"]
                 [ring/ring-defaults "0.2.3"]
                 [ring-logger "0.7.7"]
                 [compojure "1.5.2"]
                 [ariane "0.1.0-SNAPSHOT"]
                 [cheshire "5.7.0"]]
  :main solar.core
  :aot [solar.core]
  :target-path "target/%s")
