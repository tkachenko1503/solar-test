(ns solar.routes
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.logger :refer [wrap-with-logger]]
            [solar.handlers :as handlers]))

(defroutes app-routes
  (GET "/search" request handlers/get-domains-statistics)
  (route/not-found "<p>Page not found.</p>"))

(def solar-routes
  (-> #'app-routes
    (wrap-defaults site-defaults)
    (wrap-with-logger)))
