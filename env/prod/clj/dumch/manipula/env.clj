(ns dumch.manipula.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init       (fn []
                 (log/info "\n-=[manipula starting]=-"))
   :start      (fn []
                 (log/info "\n-=[manipula started successfully]=-"))
   :stop       (fn []
                 (log/info "\n-=[manipula has shut down successfully]=-"))
   :middleware (fn [handler _] handler)
   :opts       {:profile :prod}})
