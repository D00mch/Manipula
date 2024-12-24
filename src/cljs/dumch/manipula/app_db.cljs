(ns dumch.manipula.app-db 
  (:require
   [alandipert.storage-atom :refer [local-storage]]
   [reagent.core :as r]))

(def web-storage (local-storage (atom {}) :prefs))

(defn ws-assoc [k v]
  (swap! web-storage assoc k v))

(defn ws-assoc-in [ks v]
  (swap! web-storage assoc-in ks v))

(defn ws-get [k]
  (-> web-storage deref (get k)))

(defn ws-get-in [ks]
  (-> web-storage deref (get-in ks)))

(def scenario-example
  (str "[{:type :open\n"
       "  :name \"Open stargate\"\n"
       "  :url  \"https://stargate.finance/transfer\"}\n]"))

(defonce state
  (r/atom {:selected-tab "Main"
           :modal nil
           :dev {:pattern-direction "Horizontal"
                 :pattern-length    60
                 :pattern           "here you will see a pattern"
                 :pattern-xy        "here you will see a pattern xy"
                 :scenario scenario-example
                 :step 0}
           :main {:execution   {:result nil
                                :error nil}
                  :compilation {:result nil
                                :error nil
                                :name->step {}}
                  :scenario  {:selected nil
                              :list []}
                  :editor nil
                  :the-key {:result nil
                            :error nil}
                  :step-name ""
                  :step 0}
           :exchange {:binance-selected-tab :exchange/coins}}))

#_(do
    (require '[clojure.pprint :refer [pprint]])
    (pprint @state))

(defn cursor [ks]
  (r/cursor state ks))

(defn get
  "Get the key's value from the session, returns nil if it doesn't exist."
  [k & [default]]
  (let [temp-a @(cursor [k])]
    (if-not (nil? temp-a) temp-a default)))

(defn assoc! [k v]
  (clojure.core/swap! state assoc k v))

(defn get-in
 "Gets the value at the path specified by the vector ks from the session,
  returns nil if it doesn't exist."
  [ks & [default]]
  (let [result @(cursor ks)]
    (if-not (nil? result) result default)))

(defn swap!
  "Replace the current session's value with the result of executing f with
  the current value and args."
  [f & args]
  (apply clojure.core/swap! state f args))

(defn clear! []
  (clojure.core/reset! state {}))

(defn reset! [m]
  (clojure.core/reset! state m))

(defn remove! [k]
  (clojure.core/swap! state dissoc k))

(defn assoc-in! [ks v]
  (clojure.core/swap! state assoc-in  ks v))

(defn get!
  "Destructive get from the session. This returns the current value of the key
  and then removes it from the session."
  [k & [default]]
  (let [cur (get k default)]
    (remove! k)
    cur))

(defn get-in!
  "Destructive get from the session. This returns the current value of the path
  specified by the vector ks and then removes it from the session."
  [ks & [default]]
    (let [cur (get-in ks default)]
      (assoc-in! ks nil)
      cur))

(defn update!
  "Updates a value in session where k is a key and f
   is the function that takes the old value along with any
   supplied args and return the new value. If key is not
   present it will be added."
  [k f & args]
  (clojure.core/swap!
    state
    #(apply (partial update % k f) args)))

(defn update-in!
  "Updates a value in the session, where ks is a
   sequence of keys and f is a function that will
   take the old value along with any supplied args and return
   the new value. If any levels do not exist, hash-maps
   will be created."
  [ks f & args]
  (clojure.core/swap!
    state
    #(apply (partial update-in % ks f) args)))
