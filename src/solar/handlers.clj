(ns solar.handlers
  (:require [org.httpkit.server :as server]
            [solar.utils :as utils]
            [solar.app :as app]
            [clojure.core.async :as async]))

(defn get-domains-statistics [req]
  "Get query, run requests and afterall send response to client"
  (server/with-channel req channel
    (let [query (get-in req [:query-params "query"])
          query-strings (utils/ensure-queries query)
          done #(server/send! channel (utils/pretify-response %))]
      (async/go
        (let [search-result-channel (app/search query-strings)
              [result _] (async/alts! [search-result-channel])]
          (done
            (app/calc-statistic result)))))))

