(ns dumch.manipula.scenario-util 
  (:require
   [clojure.string :as str]
   [clojure.walk :as walk]))

(defn actions->steps [data]
  (loop [[{-name :name actions :actions} & rst :as queue]  data
         result []
         order  0]
    (if (seq queue)
      (let [inner-actions (or actions [])]
        (recur (concat inner-actions rst)
               (conj result [-name order])
               (inc order)))
      result)))

(defn actions->map [actions]
  {:pre [(sequential? actions)]}
  (reduce
   (fn [acc action]
     (if (and (map? action) (:name action) (> (count action) 1))
       (cond-> (assoc acc (:name action) action)
         (= (:type action) :group)
         (merge acc (actions->map (:actions action))))
       acc))
   {}
   actions))

(def ^{:const true, :doc "Alphanumeric chars in the ASCII-code order: 0-9,A-Z,a-z"}
  alphanumeric
  "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz")

(def ^:const BASE (count alphanumeric))

(defn rand-char [] (nth alphanumeric (rand-int BASE)))
(defn rand-str [length] (str/join (repeatedly length rand-char)))

(def ^:const copy-prefix "_copy#")

(defn name->copy [-name]
  (str -name copy-prefix (rand-str 5)))

(defn copy->name [copy]
  (str/replace copy (re-pattern (str copy-prefix ".*")) ""))

(defn extend-actions
  "Augments actions with additional keys from actions with the same name.
  Works recursively, also extending nested actions within :actions keys.
  
  `[{:type :open,
    :name \"Open stargate\",
    :url \"https://stargate.finance/transfer\"} 
   {:name \"Open stargate\"}]`

  In the above example, the second action will be augmented."
  [actions]
  {:pre [(sequential? actions)]}
  (let [actions-by-name (actions->map actions)
        seen (transient #{})]
    (walk/prewalk
     (fn [node]
       (let [a-name  (:name node)
             initial (and a-name (get actions-by-name (:name node)))]
         (cond (nil? a-name)
               node

               (and (:type node) (not (seen a-name)))
               (and (conj! seen a-name)
                    node)

               :else
               (assoc (merge initial node)
                      :name (name->copy a-name)))))
     actions)))

(comment
  (extend-actions
   [{:name "open spotlight"}
    {:type :keyboard, :name "open spotlight", :keys [[:cmd :space]]}
    {:name "open spotlight"}])

  (actions->steps
   [{:type :group
     :name "Group1"
     :actions [{:type    :group
                :name    "Group2"
                :actions [{:name "mouse",
                           :type :mouse-move,
                           :xy   [100 100]}]
                :check   {:type :screen-size :xy   [1024 960]}
                :times 2}
               {:name "open spotlight"},
               {:name "select chrome"}]}
    {:type :keyboard, :name "open spotlight", :keys [[:cmd :space]]}
    {:type :keyboard, :name "select chrome",  :keys ["google chrome" :enter]}])

  (actions->steps
   [{:type :group
     :name "neovide group2"
     :check {:a 1}
     :actions [{:type :group
                :name "inner action_2"
                :actions [{:type :keyboard, :name "first!" :keys ["neovide" :enter]}
                          {:name "inner2 action_2"
                           :type :group
                           :actions
                           [{:type :keyboard, :name "k_2", :keys [[:cmd :space]]}]}]},

               {:type :keyboard, :name "nvim_2" :keys ["neovide" :enter]}]}
    {:type :keyboard, :name "third" :keys ["neovide" :enter]}
    {:type :group
     :name "neovide group"
     :check {:a 1}
     :actions [{:type :group
                :name "inner action"
                :actions [{:name "inner2 action"
                           :type :group
                           :actions
                           [{:type :keyboard, :name "k", :keys [[:cmd :space]]}]}]},

               {:type :keyboard, :name "nvim" :keys ["neovide" :enter]}]}
    {:type :keyboard, :name "nvim2" :keys ["neovide" :enter]}])

  ;;
  )
