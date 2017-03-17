(ns solar.utils
  (:require [cheshire.core :as json]
            [clojure.string :as str]))

(defn pretify-response
  "Format json response"
  [data]
  (let [content (json/generate-string data {:pretty true})]
    {:status 200
     :body (str "<pre><code>"
                content
                "</code></pre>")}))

(defn ensure-queries
  "Ensures thet query is vector"
  [query]
  (cond
    (vector? query) query
    (string? query) [query]
    :else []))

(defn key-val-string
  "Build key=val string"
  [[key val]]
  (str (name key) "=" val))

(defn build-full-url
  "Build full url with queries"
  [base params]
  (str base "?"
       (str/join "&" (map key-val-string params))))
