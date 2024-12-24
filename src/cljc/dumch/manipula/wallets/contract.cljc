(ns dumch.manipula.wallets.contract
  (:require
   [clojure.spec.alpha :as s]))

(def ^:const evm "EVM")
(def ^:const braavos "Braavos")
(def ^:const argentx "Argentx")

(s/def ::wallet-type #{evm braavos argentx})
