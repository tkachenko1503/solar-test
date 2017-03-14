(ns solar.core
  (:gen-class)
  (:require [org.httpkit.server :as server]
            [org.httpkit.client :as client]
            [clojure.core.async :refer [chan go go-loop >! <! close! alts! pipe timeout] :as async]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [clojure.java.io :refer [as-url]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.logger :refer [wrap-with-logger]]
            [ariane.core :as ariane]))


(def request-queue (atom {:tasks-list          []
                          :running-tasks-count 0}))


(defn add-task
  "Adds task to task-list"
  [queue task]
  (update queue :tasks-list conj task))


(defn inc-running-tasks
  "Increments running tasks count"
  [queue]
  (update queue :running-tasks-count inc))


(defn dec-running-tasks
  "Decrements running tasks count"
  [queue]
  (update queue :running-tasks-count dec))


(defn pop-task
  "Removes first task from queue"
  [queue]
  (update queue :tasks-list (comp vec rest)))


(def proccess-queue-step (comp inc-running-tasks pop-task))


(defn handle-task
  "Final task proccess step"
  [task-channel]
  (fn [request-result]
    (go
      (>! task-channel request-result)
      (close! task-channel)
      (swap! request-queue dec-running-tasks))))


(defn add-to-queue
  "Add task to queue"
  [task]
  (let [task-channel (chan)
        handler (handle-task task-channel)
        task-with-handler (conj task handler)]
    (swap! request-queue add-task task-with-handler)
    task-channel))


(defn run-scheduler
  "Start watching and run tasks"
  [{:keys [concurrency]}]
  (go-loop []
    (if-let [task (-> @request-queue :tasks-list first)]
      (if (< (:running-tasks-count @request-queue) concurrency)
        (do
          (swap! request-queue proccess-queue-step)
          (apply (first task) (rest task))
          (recur))
        (recur))
      (do
        (<! (timeout 100))
        (recur)))))


(defn request
  "Make api request"
  [url params done]
  (log/info (str "--SEND request for " (get-in params [:query-params :text])))
  (client/get url
              params
              (fn [{:keys [status headers body error]}]
                (log/info (str "--RESIVE response for " (get-in params [:query-params :text])))
                (if error (log/error error))
                (let [request-result (if error "" body)]
                  (done request-result)))))


(defn add-request-to-queue
  "Add request call to queue"
  [query]
  (let [task [request
              "http://blogs.yandex.ru/search.rss"
              {:query-params {:text query
                              :numdoc 10
                              :p 1}}]]
    (add-to-queue task)))


(defn collect-task-results
  "Return channel with vector of all tasks results"
  [tasks]
  (let [tasks-results-channel (chan)]
    (go
      (let [results (<! (async/map (partial conj []) tasks))]
        (log/info "--STOP collecting")
        (>! tasks-results-channel results)
        (close! tasks-results-channel)))
    tasks-results-channel))


(defn search [query-strings proccess-search-result]
  "Parallelize queries and handle responses"
    (let [result-channel (chan)
          tasks (mapv add-request-to-queue query-strings)
          tasks-results (collect-task-results tasks)]
      (pipe tasks-results result-channel)
      result-channel))


(defn ensure-queries
  "Ensures thet query is vector"
  [query]
  (cond
    (vector? query) query
    (string? query) [query]
    :else []))


(defn json-response
  "Format json response"
  [body]
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    body})


(defn calc-statistic
  "Calculates domains statistics"
  [search-results]
  (let [feeds (->> search-results
                   (map #(java.io.ByteArrayInputStream. (.getBytes %)))
                   (map ariane/parse)
                   (map :entries)
                   flatten
                   (map (comp :href first :links))
                   (map #(.getHost (as-url %))))]
    feeds))


(defn get-domains-statistics [req]
  "Get query, run requests and afterall send response to client"
  (server/with-channel req channel
    (let [query (get-in req [:query-params "query"])
          query-strings (ensure-queries query)
          done #(server/send! channel (json-response %))]
      (go
        (let [search-result-channel (search query-strings calc-statistic)
              [result _] (alts! [search-result-channel])]
          (-> result
               calc-statistic
               json/write-str
               done))))))


(defroutes app-routes
  (GET "/search" request get-domains-statistics)
  (route/not-found "<p>Page not found.</p>"))


(defn run-server []
  "Run web server with configured handler"
  (server/run-server (-> #'app-routes
                        (wrap-defaults site-defaults)
                        (wrap-with-logger))
                     {:port 8080}))


(defn -main
  "Entry point"
  [& args]
  (run-scheduler {:concurrency 10})
  (run-server)
  (log/info "server running on port 8080"))
