(ns dumch.manipula.pages.diff
  (:require
   [clojure.string :as str]
   [clojure.walk :as walk]
   [dumch.manipula.app-db :as db]
   [dumch.manipula.components.editor :as editor]
   [dumch.manipula.flat-actions :as flat]
   [dumch.manipula.util :as util]
   [reagent.core :as r]))

(defn short-keys [coll]
  (walk/postwalk
   (fn [x] (if (map? x) (update-keys x (comp keyword name)) x))
   coll))

(defn- get-view [a-or-b]
  (let [merge-view (db/get-in [:diff :editor])]
    (case a-or-b
      :a (.-a merge-view)
      :b (.-b merge-view))))

(defn- get-code [a-or-b]
  (editor/get-value (get-view a-or-b)))

(defn- set-code! [a-or-b code]
  (editor/set-value! (get-view a-or-b) code))

(defn- cm->bytecode! [path]
  (let [cm-value               (get-code path)
        {:keys [result error]} (util/compile cm-value :safe? true)]
    (db/ws-assoc-in [:diff path :prev-scenario] cm-value)
    (if error
      (db/assoc-in! [:diff path :error] error)
      (let [bytecode (->> result
                          flat/decompose-action
                          flat/merge-waits
                          short-keys
                          (map (fn [{{safe :safe} :meta :as atomic}]
                                 (if safe
                                   (assoc atomic :safe safe)
                                   atomic)))
                          (map #(dissoc % :meta))
                          str
                          util/coerce-clj-or-error!)]
        (db/assoc-in! [:diff :is-bytecode] true)
        (set-code! path bytecode)))))

(defn- cm->prev-scenario [path]
  (set-code! path (db/ws-get-in [:diff path :prev-scenario])))

(defn diff-page []
  (r/with-let [_ (db/assoc-in! [:diff :is-bytecode] false)]
    [:div
     [:div.columns
      [:div.column.is-narrow
       [:button.button.is-info.is-small
        {:disabled (db/get-in [:diff :is-bytecode])
         :on-click (fn []
                     (cm->bytecode! :a)
                     (cm->bytecode! :b))}
        "Bytecode compare"]]
      [:div.column.is-narrow
       [:button.button.is-danger.is-small
        {:disabled (not (db/get-in [:diff :is-bytecode]))
         :on-click (fn []
                     (db/update-in! [:diff :is-bytecode] not)
                     (cm->prev-scenario :a)
                     (cm->prev-scenario :b))}
        "Revert"]]]

     (when (or (db/get-in [:diff :left :error])
               (db/get-in [:diff :right :error]))
       [:div.columns
        [:div.column.is-half
         [:pre {:style {:white-space "pre-wrap"}}
          (str/replace (str (db/get-in [:diff :left :error])) "\\n" "\n")]]
        [:div.column.is-half
         [:pre {:style {:white-space "pre-wrap"}}
          (str/replace (str (db/get-in [:diff :right :error])) "\\n" "\n")]]])

     [editor/merge-view
      [:diff :editor]
      (or
       (db/ws-get-in [:diff :a :prev-scenario])
       (util/coerce-clj-or-error!
        (str
         [{:type :keyboard :name "test2" :keys [100]}
          {:type :keyboard
           :name "test"
           :keys
           [[:cmd :space] "ya.ru" 1000 :enter [:tab 2] [10 20] :mouse1]}
          {:type :mouse-move :name "mmove" :xy [10 10]}
          {:type :click :name "click" :xy [10 10]}])))
      (or
       (db/ws-get-in [:diff :b :prev-scenario])
       (util/coerce-clj-or-error!
        (str
         [{:type :keyboard :name "test2" :keys [100]}
          {:type :group
           :name "Group1"
           :repeat-unless {:check {:type :opened! :regexp "ya.ru"}
                           :times 10}
           :actions [{:type :keyboard
                      :name "test"
                      :delay 111
                      :wait 333
                      :keys [[:cmd :space] "ya.ru" 1000 :enter [:tab 2]]}
                     {:type :keyboard
                      :name "test2"
                      :keys [[10 20] :mouse1]}]}
          {:type :mouse-move, :name "mmove", :xy [10 10]}
          {:type :click, :name "click", :xy [10 10]}])))]]
    (finally
      (when-not (db/get-in [:diff :is-bytecode])
        (db/ws-assoc-in [:diff :b :prev-scenario] (get-code :b))
        (db/ws-assoc-in [:diff :a :prev-scenario] (get-code :a))))))


#_(.-state (.-a (db/get-in [:diff :editor])))
