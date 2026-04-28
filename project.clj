(defproject my-sns "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/core.async "1.5.648"]
                 [org.clojure/data.json "2.5.2"]
                 [http-kit "2.8.1"]
                 [com.github.seancorfield/next.jdbc "1.3.894"]
                 [com.taoensso/carmine "3.3.1"]
                 [org.postgresql/postgresql "42.7.3"]
                 [metosin/reitit "0.6.0"]
                 [metosin/reitit-ring "0.6.0"]
                 [ring/ring-jetty-adapter "1.9.6"]
                 [ring/ring-json "0.5.1"]
                 [ring-cors "0.1.13"]
                 [aero "1.1.6"]
                 [com.github.seancorfield/honeysql "2.7.1368"]
                 [clj-commons/clj-yaml "0.7.1"]
                 [buddy/buddy-hashers "2.0.167"]
                 [buddy/buddy-auth "3.0.1"]]
  :main my-sns.core
  :profiles {:dev {:resource-paths ["dev-resources"]}
             :test {:resource-paths ["test-resources"]}})
