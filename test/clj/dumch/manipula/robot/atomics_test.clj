(ns dumch.manipula.robot.atomics-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [dumch.manipula.types]
   [dumch.manipula.robot.atomic-action :as atomic-action]))

(comment
  (require 'pjstadig.humane-test-output)
  (pjstadig.humane-test-output/activate!))

(defn perform-with-success [returns performed-atomics]
  (fn [{-type :atomic/type
        {times :repeat/times :as data} :atomic/data
        {-name :action/name} :atomic/meta}]
    (cond
      (= -type :atomic/wait)
      nil

      (and (= -type :atomic/repeat) (<= times 0))
      (throw (ex-info "Repeat checks failed" {}))

      :else
      (swap! performed-atomics
             conj
             {:name (str/replace -name #"#.*" "")
              :type (keyword (name -type))}))
    returns))

(def scenario-input
  {:actions
   [{:type :keyboard, :name "open spotlight", :keys [[:cmd :space]]}
    {:type :keyboard, :name "select chrome",  :keys ["google chrome" :enter]}

    {:type :group
     :name "Group1"
     :actions [{:type          :group
                :name          "Group2"
                :actions       [{:name "mouse",
                                 :type :mouse-move,
                                 :xy   [100 100]}]
                :repeat-unless {:check {:type :screen-size
                                        :xy   [1024 960]}
                                :times 2}}
               {:name "open spotlight"},
               {:name "select chrome"}]
     :repeat-unless {:check {:type :opened!
                             :regexp "ya.ru"}
                     :times 1}}]

   :step    0})

(deftest test-repeat-unless
  (testing "Should not repeat-unless as check succeed"
    (let [performed-atomics (atom [])]
      (with-redefs [atomic-action/perform-atomic-record!
                    (perform-with-success true performed-atomics)]
        (atomic-action/perform-with-continuation-check-input!
         scenario-input
         (atom false))

        (is (= [{:name "open spotlight",     :type :hotkey}
                {:name "select chrome",      :type :text}
                {:name "select chrome",      :type :key}
                {:name "mouse",              :type :move}
                {:name "Group2",             :type :repeat}
                {:name "open spotlight_copy",:type :hotkey}
                {:name "select chrome_copy", :type :text}
                {:name "select chrome_copy", :type :key}
                {:name "Group1",             :type :repeat}]
               @performed-atomics)))))

  (testing "Should repeat-unless on failed checks"
    (let [performed-atomics (atom [])]
      (with-redefs [atomic-action/perform-atomic-record!
                    (perform-with-success false performed-atomics)]
        (is (thrown?
             clojure.lang.ExceptionInfo
             (atomic-action/perform-with-continuation-check-input!
              scenario-input
              (atom false))))

        (is (= [{:name "open spotlight", :type :hotkey}
                {:name "select chrome", :type :text}
                {:name "select chrome", :type :key}
                {:name "mouse", :type :move}
                {:name "Group2", :type :repeat}
                {:name "mouse", :type :move}
                {:name "Group2", :type :repeat}
                {:name "mouse", :type :move}]
               @performed-atomics))))))

(def scenario-input-repeat-test
  {:actions
   [{:type :keyboard, :name "spotlight", :keys [[:cmd :space]]}
    {:type :group
     :name "neovide group"
     :actions [{:name "spotlight"}
               {:name "cmd+t", :type :keyboard, :keys [[:cmd :t]],
                :regroup {:check {:type :opened! :regexp "ya.ru"}
                          :times 2}}
               {:name "mouse" :type :mouse-move :xy [1 2]}]}
    {:name "mouse2" :type :mouse-move :xy [1 2]}]
   :step    0})

(deftest test-regroup
  (testing "Should not repeat when check suceed"
    (let [performed-atomics (atom [])]
      (with-redefs [atomic-action/perform-atomic-record!
                    (perform-with-success true performed-atomics)]
        (atomic-action/perform-with-continuation-check-input!
         scenario-input-repeat-test
         (atom false))

        (is (=
             [{:name "spotlight", :type :hotkey}
              {:name "spotlight_copy", :type :hotkey}
              {:name "cmd+t", :type :repeat}
              {:name "cmd+t", :type :hotkey}
              {:name "mouse" :type :move}
              {:name "mouse2" :type :move}]
             @performed-atomics)))))
  (testing "Should repeat when check failed"
    (let [performed-atomics (atom [])]
      (with-redefs [atomic-action/perform-atomic-record!
                    (perform-with-success false performed-atomics)]
        (is (thrown?
             clojure.lang.ExceptionInfo
             (atomic-action/perform-with-continuation-check-input!
              scenario-input-repeat-test
              (atom false))))

        (is (=
             [{:name "spotlight", :type :hotkey}
              {:name "spotlight_copy", :type :hotkey}
              {:name "cmd+t", :type :repeat}
              {:name "spotlight_copy", :type :hotkey}
              {:name "cmd+t", :type :repeat}
              {:name "spotlight_copy", :type :hotkey}]
             @performed-atomics))))))

(def scenario-input-safe-inner-group
  {:actions
   [{:type :group
     :name "Group1"
     :actions [{:type    :group
                :name    "Group2"
                :actions
                [{:name "mouse", :type :mouse-move, :xy [1 1]}]}
               {:name "open spotlight"},
               {:name "select chrome"}]}

    {:type :keyboard, :name "open spotlight", :keys [[:cmd :space]]}
    {:type :keyboard, :name "select chrome",  :keys ["chrome" :enter]}]
   :step    0})

(deftest test-safe-groups
  (let [check {:type :screen-size :xy [1024 960]}]
    (testing "Should skip child group with failed check"
      (let [performed-atomics
            (atom [])]

        (with-redefs [atomic-action/perform-atomic-record!
                      (perform-with-success false performed-atomics)]
          (atomic-action/perform-with-continuation-check-input!
           (-> scenario-input-safe-inner-group
               (assoc-in [:actions 0 :actions 0 :safe] true)
               (assoc-in [:actions 0 :actions 0 :check] check))
           (atom false))

          (is (= [{:name "Group2", :type :check}
                  {:name "open spotlight_copy", :type :hotkey}
                  {:name "select chrome_copy", :type :text}
                  {:name "select chrome_copy", :type :key}
                  {:name "open spotlight", :type :hotkey}
                  {:name "select chrome", :type :text}
                  {:name "select chrome", :type :key}]
                 @performed-atomics)))))

    (testing "Should skip the whole parent group when child's check failed"
      (let [performed-atomics (atom [])]
        (with-redefs [atomic-action/perform-atomic-record!
                      (perform-with-success false performed-atomics)]
          (atomic-action/perform-with-continuation-check-input!
           (-> scenario-input-safe-inner-group
               (assoc-in [:actions 0 :safe] true)
               (assoc-in [:actions 0 :actions 0 :actions 0 :check] check))
           (atom false))

          (is (= [{:name "mouse" #_mouse_check, :type :check}
                  {:name "open spotlight", :type :hotkey}
                  {:name "select chrome", :type :text}
                  {:name "select chrome", :type :key}]
                 @performed-atomics))))
      (let [performed-atomics (atom [])]
        (with-redefs [atomic-action/perform-atomic-record!
                      (perform-with-success false performed-atomics)]
          (atomic-action/perform-with-continuation-check-input!
           (-> scenario-input-safe-inner-group
               (assoc-in [:actions 0 :safe] true)
               (assoc-in [:actions 0 :actions 0 :check] check))
           (atom false))

          (is (= [{:name "Group2" #_mouse_check, :type :check}
                  {:name "open spotlight", :type :hotkey}
                  {:name "select chrome", :type :text}
                  {:name "select chrome", :type :key}]
                 @performed-atomics)))))

    (testing "Should skip the whole parent group with the group's failed check"
      (let [performed-atomics (atom [])]
        (with-redefs [atomic-action/perform-atomic-record!
                      (perform-with-success false performed-atomics)]
          (atomic-action/perform-with-continuation-check-input!
           (-> scenario-input-safe-inner-group
               (assoc-in [:actions 0 :safe] true)
               (assoc-in [:actions 0 :check] check))
           (atom false))

          (is (= [{:name "Group1", :type :check}
                  {:name "open spotlight", :type :hotkey}
                  {:name "select chrome", :type :text}
                  {:name "select chrome", :type :key}]
                 @performed-atomics)))))

    (testing "Should skip safe action with failed check"
      (let [performed-atomics
            (atom [])

            scenario
            {:actions
             [{:type :keyboard, :name "space", :keys [:space]
               :safe true, :check check}
              {:type :keyboard, :name "tab", :keys [:tab]}]
             :step 0}]
        (with-redefs [atomic-action/perform-atomic-record!
                      (perform-with-success false performed-atomics)]
          (atomic-action/perform-with-continuation-check-input!
           scenario
           (atom false))

          (is (= @performed-atomics
                 [{:name "space", :type :check}
                  {:name "tab", :type :key}])))))))
