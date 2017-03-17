(ns solar.queue
  (:require [clojure.core.async :as async]))

(defn- queue-state
  "Creates new queue state"
  []
  (atom {:tasks-list []
         :running-tasks-count 0}))

(defn- add-task
  "Adds task to task-list"
  [queue task]
  (update queue :tasks-list conj task))

(defn- inc-running-tasks
  "Increments running tasks count"
  [queue]
  (update queue :running-tasks-count inc))

(defn- dec-running-tasks
  "Decrements running tasks count"
  [queue]
  (update queue :running-tasks-count dec))

(defn- pop-task
  "Removes first task from queue"
  [queue]
  (update queue :tasks-list (comp vec rest)))

(def proccess-queue-step
  (comp inc-running-tasks pop-task))

(defn- handle-task
  "Final task proccess step"
  [state task-channel]
  (fn [task-result]
    (async/go
      (async/>! task-channel task-result)
      (async/close! task-channel)
      (swap! state dec-running-tasks))))

(defn- run-queue-scheduler
  "Start watching and run tasks"
  [state concurrency]
  (async/go-loop []
    (if-let [task (-> @state :tasks-list first)]
      (if (< (:running-tasks-count @state) concurrency)
        (do
          (swap! state proccess-queue-step)
          (apply (first task) (rest task))
          (recur))
        (recur))
      (do
        (async/<! (async/timeout 100))
        (recur)))))


(defn add-to-queue
  "Add task to queue and returns task channel"
  [state task]
  (let [task-channel (async/chan)
        handler (handle-task state task-channel)
        task-with-handler (conj task handler)]
    (swap! state add-task task-with-handler)
    task-channel))

(defn collect-task-results
  "Return channel with vector of all tasks results"
  [tasks]
  (let [tasks-results-channel (async/chan)]
    (async/go
      (let [results (async/<! (async/map (partial conj []) tasks))]
        (async/>! tasks-results-channel results)
        (async/close! tasks-results-channel)))
    tasks-results-channel))

(defn init-queue
  "Create and run new queue"
  [{:keys [concurrency]}]
  (let [state (queue-state)]
    (run-queue-scheduler state concurrency)
    state))
