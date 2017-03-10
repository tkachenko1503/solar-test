(ns solar.core
  (:gen-class)
  (:require [org.httpkit.server :as server]
            [org.httpkit.client :as client]
            [clojure.core.async :refer [chan go go-loop >! <! close! alts! pipe timeout]]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.logger :refer [wrap-with-logger]]))


(def request-queue (atom {:concurrency         10
                          :tasks-list          []
                          :running-tasks-count 0}))


(defn add-to-queue
  "Add task to queue"
  [task]
  (let [task-channel (chan)
        task-with-channel (conj task task-channel)]
    (swap! request-queue update :tasks-list conj task-with-channel)
    task-channel))


(defn inc-running-tasks
  "Increments running tasks count"
  []
  (swap! request-queue update :running-tasks-count inc))


(defn dec-running-tasks
  "Decrements running tasks count"
  []
  (swap! request-queue update :running-tasks-count dec))

(defn pop-task
  "Removes first task from queue"
  []
  (swap! request-queue update :tasks-list (comp vec rest)))


(go-loop []
  (if (< (:running-tasks-count @request-queue) (:concurrency @request-queue))
    (if-let [[f & args] (-> @request-queue :tasks-list first)]
      (do
        (apply f args)
        (pop-task)
        (inc-running-tasks)
        (recur))
      (do
        (<! (timeout 100))
        (recur)))
    (do
      (recur))))


(defn request
  "Make api request"
  [url params channel]
  (log/info (str "--SEND request to " url "/" (get-in params [:query-params :text])))
  (client/get url
              params
              (fn [{:keys [status headers body error]}]
                (if error (log/error error))
                (let [request-result (if error "" body)]
                  (go (>! channel request-result)
                      (close! channel))))))


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
  (let [tasks-results-channel (chan)
        tasks-results (atom [])]
    (go-loop [[result _] (alts! tasks)]
      (if result
        (do
          (dec-running-tasks)
          (swap! tasks-results conj result)
          (recur (alts! tasks)))
        (do
          (>! tasks-results-channel @tasks-results)
          (close! tasks-results-channel))))
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
  {"lenta.ru" 5
   "livejournal.com" 10
   "vk.com" 20})


(defn get-domains-statistics [req]
  "Get query, run requests and afterall send response to client"
  (server/with-channel req channel
    (let [query (get-in req [:query-params "query"])
          query-strings (ensure-queries query)
          done #(server/send! channel (json-response %))]
      (go
        (let [search-result-channel (search query-strings calc-statistic)
              [result _] (alts! [search-result-channel (timeout 5000)])]
          (done (json/write-str result)))))))


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
  (run-server)
  (log/info "server running on port 8080"))
