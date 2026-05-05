(ns my-sns.db
  (:require [hikari-cp.core :as hikari]
            [aero.core :as aero]
            [clojure.java.io :as io])
  (:import [java.io PrintWriter]
           [javax.sql DataSource]))

(defn- load-config []
  (if-let [resource (io/resource "my-sns.edn")]
    (aero/read-config resource)
    {:db-master-url "jdbc:postgresql://localhost:5432/mysns_db?user=dev&password=password"
     :db-replica-url "jdbc:postgresql://localhost:5432/mysns_db?user=dev&password=password"}))

(defn- make-ds [url max-pool-size]
  (hikari/make-datasource
   {:jdbc-url url
    :maximumPoolSize max-pool-size}))

(defn- lazy-ds [url max-pool-size]
  (let [delegate (delay (make-ds url max-pool-size))]
    (reify DataSource
      (getConnection [_]
        (.getConnection ^DataSource @delegate))
      (getConnection [_ username password]
        (.getConnection ^DataSource @delegate username password))
      (getLogWriter [_]
        (.getLogWriter ^DataSource @delegate))
      (setLogWriter [_ writer]
        (.setLogWriter ^DataSource @delegate ^PrintWriter writer))
      (setLoginTimeout [_ seconds]
        (.setLoginTimeout ^DataSource @delegate seconds))
      (getLoginTimeout [_]
        (.getLoginTimeout ^DataSource @delegate))
      (getParentLogger [_]
        (.getParentLogger ^DataSource @delegate))
      (unwrap [_ iface]
        (.unwrap ^DataSource @delegate iface))
      (isWrapperFor [_ iface]
        (.isWrapperFor ^DataSource @delegate iface)))))

(defonce master-ds (lazy-ds (or (System/getenv "DATABASE_MASTER_URL")
                                (:db-master-url (load-config))) 10))
(defonce replica-ds (lazy-ds (or (System/getenv "DATABASE_REPLICA_URL")
                                 (:db-replica-url (load-config))) 10))

(def ^:dynamic *current-db* replica-ds)

(defmacro with-master
  [& body]
  `(binding [*current-db* master-ds]
     ~@body))
