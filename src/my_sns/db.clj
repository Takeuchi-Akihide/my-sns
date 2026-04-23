(ns my-sns.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defn- postgres-spec
  []
  (let [db-url (or (System/getenv "DATABASE_URL")
                   "jdbc:postgresql://localhost:5432/mysns_db?user=dev&password=password")]
    {:jdbcUrl db-url}))

(def datasource (jdbc/get-datasource (postgres-spec)))

(def ^:dynamic *tx* nil)

(defn query
  "SQLクエリを実行して結果をMapのリストで返す"
  [sql-params]
  (jdbc/execute! (or *tx* datasource) sql-params
                 {:builder-fn rs/as-unqualified-lower-maps}))

(defn query-one
  "SQLクエリを実行して最初の一件を返す"
  [sql-params]
  (jdbc/execute-one! (or *tx* datasource) sql-params
                     {:builder-fn rs/as-unqualified-lower-maps}))
