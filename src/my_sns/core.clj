(ns my-sns.core
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [my-sns.handler :refer [app]]
            [my-sns.schema :as schema])
  (:gen-class))

(defn -main
  "Main entry point for lein run commands"
  [& args]
  (case (first args)
    "server" (do
               (println "Starting my-sns server...")
               (schema/create-schema!) ; Ensure schema exists
               (run-jetty app {:port (Integer/parseInt (or (System/getenv "PORT") "3030"))
                               :join? true}))
    "recreate" (do
                 (println "Dropping schema...")
                 (my-sns.schema/drop-schema!)
                 (my-sns.schema/create-schema!))
    (println "Usage: lein run server|recreate")))
