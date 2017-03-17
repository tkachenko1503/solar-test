(ns solar.core
  (:gen-class)
  (:require [solar.routes :refer [solar-routes]]
            [solar.app :refer [init-app]]
            [org.httpkit.server :as server]
            [clojure.tools.logging :as log]))

(defn run-server []
  "Run web server with configured handler"
  (server/run-server solar-routes
                     {:port 8080}))

(defn -main
  "Entry point"
  [& args]
  (init-app)
  (run-server)
  (log/info "Server running on port 8080"))
