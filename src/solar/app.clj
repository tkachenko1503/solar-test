(ns solar.app
  (:require [clojure.core.async :as async]
            [solar.utils :as utils]
            [solar.queue :as queue]
            [ariane.core :as ariane]
            [clojure.java.io :as io])
  (:import (java.io ByteArrayInputStream)))

(def yandex-base-url
  "https://yandex.ru/blogs/rss/search")

(def app-state
  (atom {}))

(defn add-queue-to-app-state
  "Add queue to app state"
  []
  (swap! app-state assoc :queue
    (queue/init-queue {:concurrency 10})))

(defn init-app
  "Prepare app state"
  []
  (add-queue-to-app-state))

(defn request
  "Make request to api"
  [base params done]
  (let [url (utils/build-full-url base params)]
    (async/go
      (done (slurp url)))))

(defn create-request-task
  "Add request call to queue"
  [query]
  (let [params {:text query
                :numdoc 10
                :p 1}]
    [request yandex-base-url params]))

(defn create-task-and-to-queue
  "Create request task and add that to queue"
  [query-string]
  (queue/add-to-queue
    (:queue @app-state)
    (create-request-task query-string)))

(defn search [query-strings]
  "Parallelize queries and handle responses"
  (let [result-channel (async/chan)
        tasks (mapv create-task-and-to-queue query-strings)
        tasks-results (queue/collect-task-results tasks)]
    (async/pipe tasks-results result-channel)
    result-channel))

(defn rss-to-stream
  "Convert rss string to stream"
  [rss-string]
  (ByteArrayInputStream. (.getBytes rss-string)))

(def get-rss-entries
  (comp :entries ariane/parse rss-to-stream))

(def extract-link
  (comp :href first :links))

(defn extract-uniq-links
  "Extracts all uniq links"
  [feeds]
  (->> feeds
       flatten
       (map extract-link)
       set))

(defn get-host-from-url
  "Extracts host from url"
  [url]
  (.getHost (io/as-url url)))

(defn count-host-appearance
  "Count host frequency"
  [feeds-links]
  (->> feeds-links
       (map get-host-from-url)
       frequencies))

(defn calc-statistic
  "Calculates domains statistics"
  [search-results]
  (let [feeds (map get-rss-entries search-results)
        all-feeds-uniq-links (extract-uniq-links feeds)
        host-appearance (count-host-appearance all-feeds-uniq-links)]
    host-appearance))
