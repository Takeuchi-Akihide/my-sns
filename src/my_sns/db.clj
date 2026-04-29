(ns my-sns.db
  (:require [hikari-cp.core :as hikari]
            [aero.core :as aero]
            [clojure.java.io :as io]))

(defn- load-config []
  (if-let [resource (io/resource "my-sns.edn")]
    (aero/read-config resource)
    {:db-master-url "jdbc:postgresql://localhost:5432/mysns_db?user=dev&password=password"
     :db-replica-url "jdbc:postgresql://localhost:5432/mysns_db?user=dev&password=password"}))

(defn- make-ds [url max-pool-size]
  (hikari/make-datasource
   {:jdbc-url url
    :maximumPoolSize max-pool-size}))

(defonce master-ds (make-ds (or (System/getenv "DATABASE_MASTER_URL")
                                (:db-master-url (load-config))) 10))
(defonce replica-ds (make-ds (or (System/getenv "DATABASE_REPLICA_URL")
                                 (:db-replica-url (load-config))) 10))

(def ^:dynamic *current-db* replica-ds)

(defmacro with-master
  [& body]
  `(binding [*current-db* master-ds]
     ~@body))
