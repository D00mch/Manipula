(ns dumch.manipula.scenario-util-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [clojure.walk :as walk]
   [dumch.manipula.scenario-util :as scenario-util]))


(def test-data
  [{:type :group
    :name "top group"
    :actions [{:type :keyboard, :name "k", :keys [[:cmd :space]]}
              {:type :keyboard, :name "chrome" :keys ["google chrome" :enter]}
              {:type :group
               :name "neovide group"
               :actions [{:name "k"}, {:name "chrome"}]
               :repeat-unless {:check {:type :opened!
                                       :regexp "ya.ru"}
                               :times 2}}]}])

(deftest actions->map-test
  (testing "Map is created with deeply nested actions"
    (is
     (= ["top group" "k" "chrome" "neovide group"]
        (-> test-data
            scenario-util/actions->map
            keys)))))

(deftest extend-actions-test
  (testing "Name actions extended and get names hash"
    (let [extended (scenario-util/extend-actions test-data)]
      (walk/prewalk
       (fn [node]
         (when (and (map? node)
                    (:name node)
                    (= (count node) 1))
           (throw (ex-info "Actions are not extended!" {:node node}))))
       extended)

      (is (= 6 (count (scenario-util/actions->steps extended)))))))

(deftest definition-from-the-future-test
  (testing "Actions should be extended even if they are defined after usage"
    (let [actions
          [{:name "WooFi: open website WooFi"}
           {:name "WooFi: open website WooFi"
            :type :keyboard
            :keys [2000 [:ctrl :t] "https://fi.woo.org/" :enter 2000 [320 199] :mouse1]}
           {:name "WooFi: open website WooFi"
            :type :keyboard
            :keys [2000 [:ctrl :t] "https://fi.woo.org/" :enter 2000 [320 199] :mouse1]}]

          extended
          (scenario-util/extend-actions actions)]
      (is (-> extended first :keys))
      (is (-> extended first :type))
      
      (is (= [true false true]
             (map #(.contains % "copy") (map :name extended)))))))

#_(scenario-util/extend-actions test-data)
