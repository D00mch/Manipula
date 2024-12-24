(ns dumch.manipula.config
  (:require
    [kit.config :as config]))

(def ^:const system-filename "system.edn")

(defn system-config
  [options]
  (config/read-config system-filename options))

(def dev-config (partial system-config {:profile :dev}))
(def prod-config (partial system-config {:profile :prod}))
