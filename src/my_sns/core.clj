(ns my-sns.core
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [my-sns.handler :refer [app]]
            [my-sns.schema :as schema]
            [my-sns.worker :as worker]
            [my-sns.sample :as sample])
  (:gen-class))

(defn -main
  "Main entry point for lein run commands"
  [& args]
  (case (first args)
    "server" (do
               (println "Starting my-sns server...")
               (schema/create-schema!)
               (worker/start-worker)
               (worker/start-sync-worker)
               (run-jetty app {:port (Integer/parseInt (or (System/getenv "PORT") "3030"))
                               :join? true}))
    "recreate" (do
                 (println "Dropping schema...")
                 (my-sns.schema/drop-schema!)
                 (my-sns.schema/create-schema!))
    "load-sample" (sample/load-sample-data)
    (println "Usage: lein run server|recreate|load-sample")))
