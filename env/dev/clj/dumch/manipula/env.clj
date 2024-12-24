(ns dumch.manipula.env
  (:require
    [clojure.tools.logging :as log]
    [dumch.manipula.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init       (fn []
                 (log/info "\n-=[manipula starting using the development or test profile]=-"))
   :start      (fn []
                 (log/info "\n-=[manipula started successfully using the development or test profile]=-"))
   :stop       (fn []
                 (log/info "\n-=[manipula has shut down successfully]=-"))
   :middleware wrap-dev
   :opts       {:profile       :dev
                :persist-data? true}})
