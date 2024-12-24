(ns dumch.manipula.exchange.contract
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]))

;; —————————————— WITHDRAW INPUT ——————————————

;; {:keys [network coin amount-range wallets]}
(s/def ::network string?)
(s/def ::coin string?)
(s/def ::amount-range (s/and (s/coll-of double)
                             #(= 2 (count %))
                             #(<= (first %) (second %))))

(defn random-ethereum-address [_]
  (str "0x" (apply str (repeatedly 40 #(rand-nth "0123456789abcdef")))))

(s/def ::evm-wallet
  (s/with-gen
    (s/and string?
           #(= 42 (count %))
           #(str/starts-with? % "0x")
           #(re-matches #"0x[a-fA-F0-9]{40}" %))
    (fn [] (gen/fmap random-ethereum-address (gen/return nil)))))

#_(gen/sample (s/gen ::evm-wallet) 5)

(s/def ::wallets (s/coll-of ::evm-wallet :distinct true))

(s/def ::withdraw-input
  (s/keys :req-un [::network ::coin ::amount-range ::wallets]))

#_(s/explain
   ::withdraw-input
   {:coin "AVAX"
    :network "AVAXC"
    :amount-range [10 12]
    :wallets ["0xF7F5AB0A48a58F485eC94a881eD1Bfe5CAda7af1"
              "0x72995c4Ea922d9f093d13CF0F35047B26f48d84A"]})

;; —————————————— API KEY and SECRET ——————————————

(s/def ::api-key string?)
(s/def ::api-secret string?)
(s/def ::api-keys-input (s/keys :req-un [::api-key ::api-secret]))
