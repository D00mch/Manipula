(ns dumch.manipula.components.common
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [dumch.manipula.app-db :as db]
   [dumch.manipula.components.editor :as editor]
   [dumch.manipula.scenario-util :as scenario-util]
   [dumch.manipula.util :as util]
   [dumch.manipula.wallets.contract :as wallets-contract]
   [reagent.core :as r]))

(def pre-style {:style {:margin-left "0px" :padding-left "0px"}})

(declare actions-tree)

(defn- styled-history [{:keys [history] [-name] :last-action}]
  (let [lines (-> history
                  str
                  util/coerce-clj-or-error!
                  str/split-lines)]
    (->> lines
         (map (fn [line]
                {:line line
                 :bold? (re-find
                         (re-pattern (str "\"" -name "\""))
                         line)
                 :color (condp re-find line
                          #":success" "green"
                          #":fail" "red"
                          "#614a34")})))))

(defn- history-form [data path]
  (when (and (map? data) (:history data))
    [:div.content.m4
     [:button {:on-click #(db/update-in! [path :ui :result] not)}
      (if (db/get-in [path :ui :result])
        "△ close"
        "▼  history")]
     (when (db/get-in [path :ui :result])
       [:<>
        [:p " "]
        (for [{:keys [line bold? color]} (styled-history data)]
          [:pre
           {:style {:color color
                    :background "white"
                    :font-weight (when bold? "bold")
                    :margin  "0"
                    :padding "0"
                    :white-space "pre-wrap"}}
           line])
        [:br]])]))

#_(styled-history (db/get-in [:dev :execution :result]))

(defn result-area [path]
  (fn []
    (when-let [data (or (db/get-in [path :execution :error])
                        (db/get-in [path :execution :result]))]
      [:div.m4
       [:div.dropdown.is-hoverable.is-down
        [:div
         [:div.dropdown-trigger
          [:span.m4
           {:style {:marting 10}
            :aria-haspopup "true",
            :aria-controls "dropdown-menu4"}
           "The last scenario execution result:"]]]
        [:div.dropdown-menu
         {:id "dropdown-menu4"
          :role "menu"
          :style {:min-width 600,
                  :border "1px solid #000"}}
         [:pre {:style {:white-space "pre-wrap"}}
          (try
            (util/coerce-clj-or-error! (str (dissoc data :history)))
            (catch js/Error e
              (str data)))]]]

       (history-form data path)])))

(defn actions-node [action path nest]
  (let [action-name (:name action)]
    (fn [action path nest]
      (let [nested-actions
            (:actions action)

            step-num
            (get (db/get-in [path :compilation :name->step]) action-name)

            display-name
            (str step-num " " action-name)

            selected?
            (= (get (db/get-in [path :compilation :name->step]) action-name)
               (db/get-in [path :step]))

            bold?
            (= (scenario-util/copy->name action-name)
               (some-> (db/get-in [path :execution :result :running 0])
                       scenario-util/copy->name))

            link-attrs
            {:class (when selected? "has-text-danger-dark")
             :style {:font-weight (when bold? "bold")}
             :on-click (fn []
                         (editor/scroll-to-name! [path :editor]
                                                 (scenario-util/copy->name action-name))
                         (db/assoc-in! [path :step-name] action-name)
                         (db/assoc-in! [path :step] step-num))}]
        (if nested-actions
          [:div
           [:button {:on-click #(db/update-in! [path :ui display-name] not)}
            (if (db/get-in [path :ui display-name]) "△ close" "▼  open")]
           [:a.m4 link-attrs display-name]
           (when (db/get-in [path :ui display-name])
             [actions-tree :actions nested-actions :path path :nest (inc nest)])]
          [:a link-attrs display-name])))))

(defn actions-tree []
  (fn [& {:keys [actions path nest] :or {nest 0}}]
    [:<>
     (for [[i action] (map-indexed vector actions)]
       ^{:key (str (:name action) i)}
       [:div.container
        {:style {:margin-left (* nest 4)}}
        [actions-node action path nest]])]))

(defn tab-page [tab-name & [on-click]]
  [:li {:class (when (= (db/get :selected-tab) tab-name) "is-active")}
   [:a {:on-click (fn []
                    (when on-click (on-click tab-name))
                    (db/assoc! :selected-tab tab-name))}
    tab-name]])

(defn dropdown-settings [& {}]
  (let [active (r/atom false)]
    (fn [& {:keys [state-path values name]}]
      [:div.dropdown
       {:class (when @active "is-active")
        :on-click #(reset! active (not @active))}
       [:div.dropdown-trigger
        [:button.button
         {:aria-haspopup "true",
          :style {:min-width 250}
          :aria-controls "dropdown-menu"}
         [:span name ": " (str/lower-case (str (db/get-in state-path)))]]]
       [:div.dropdown-menu {:id "dropdown-menu"
                            :role "menu"}
        [:div.dropdown-content
         (doall
          (for [v values]
            ^{:key v}
            [:a.dropdown-item
             {:class    (when (-> (db/get-in state-path) (= v))
                          "is-active")
              :on-click #(db/swap! assoc-in state-path v)}
             (str v)]))]]])))

;; -------------------------
;; Modal form

(defn modal [& {:keys [header body footer style]}]
  [:div.modal.is-active.disable-selection
   [:div.modal-background
    {:on-click #(db/remove! :modal)}]
   [:div.modal-card
    (merge {:style {:width "95vw" :max-width "600px"}} style)
    [:header.modal-card-head header]
    [:section.modal-card-body body]
    [:footer.modal-card-foot footer]]])

(defn the-key-form [send-key-fn request-key-fn]
  (let [key-atom (r/atom nil)
        loading  (r/atom nil)]
    (fn []
      [modal
       :header [:div "Enter the private key"]
       :body   [:div
                [:textarea.textarea
                 {:placeholder "It will be stored only in memory"
                  :on-change (fn [e]
                               (let [value (.. e -target -value)]
                                 (reset! key-atom value)))}]]
       :footer [:div
                [:a.button.is-primary.is-small
                 {:class (when @loading "is-loading")
                  :on-click (fn [_]
                              (send-key-fn @key-atom))}
                 "Send"]
                [:a.button.is-primary.is-small
                 {:class (when @loading "is-loading")
                  :on-click (fn [_]
                              (request-key-fn))}
                 "Request"]
                [:a.button.is-danger.is-small
                 {:on-click #(db/remove! :modal)}
                 "Close"]

                (when-let [e (db/get-in [:main :the-key :error])]
                  [:p (str e)])
                
                (when-let [result (db/get-in [:main :the-key :result])]
                  [:p result])
                
                (when-let [result (db/get-in [:main :the-key-request])]
                  [:p (str result)])]])))

(defn request-seeds-form [request-fn]
  (db/assoc-in! [:main :seed-request] nil)
  (let [wallet-atom (r/atom wallets-contract/evm)
        profile-atom (r/atom nil)
        loading  (r/atom nil)
        on-click (fn []
                   (reset! loading true)
                   (request-fn {:wallet-type @wallet-atom
                                :id-profile @profile-atom}))]
    (fn []
      [modal
       :header [:div "Request seeds. Select wallet type and enter the profile id"]
       :body   [:div.columns
                [:div.column.is-one-quarter
                 [:div.select
                  {:value @wallet-atom
                   :on-change #(reset! wallet-atom (-> % .-target .-value))}
                  [:select
                   [:option wallets-contract/evm]
                   [:option wallets-contract/braavos]
                   [:option wallets-contract/argentx]]]]
                [:div.column
                 [:input.input
                  {:type :number
                   :placeholder "ADS profile id"
                   :on-key-press (fn [e]
                                   (when (= (.-key e) "Enter")
                                     (on-click)))
                   :on-change (fn [e]
                                (let [value (.. e -target -value)]
                                  (reset! profile-atom value)))}]]]
       :footer [:div
                [:a.button.is-primary.is-small
                 {:class (when @loading "is-loading")
                  :disabled (empty? (str/trim (str @profile-atom)))
                  :on-click on-click}
                 "Request"]
                [:a.button.is-danger.is-small
                 {:on-click #(db/remove! :modal)}
                 "Close"]

                (when-let [result (db/get-in [:main :seed-request])]
                  (reset! loading false)
                  [:p (str result)])]])))

(defn modal-with-action
  [title-txt button-txt send-key-fn input-spec & [input-type]]
  (let [key-atom (r/atom nil)
        clicked  (r/atom false)
        errors   (r/atom nil)
        on-click #(cond
                    @clicked
                    (js/alert "You clicked already!")

                    (s/valid? input-spec @key-atom)
                    (do
                      (reset! errors nil)
                      (reset! clicked true)
                      (send-key-fn @key-atom))

                    :else
                    (reset! errors (str (s/explain-str input-spec @key-atom))))]
    (fn []
      [modal
       :header [:div title-txt]
       :body   [:div
                [:input.input
                 {:type input-type
                  :on-key-press (fn [e]
                                    (when (= (.-key e) "Enter")
                                      (on-click)))

                  :on-change (fn [e]
                               (let [value (.. e -target -value)]
                                 (reset! clicked false)
                                 (reset! key-atom value)))}]]
       :footer [:div
                [:a.button.is-primary.is-small
                 {:on-click on-click}
                 button-txt]
                [:a.button.is-danger.is-small
                 {:on-click #(db/remove! :modal)}
                 "Close"]
                (when-let [e @errors]
                  [:p (str e)])]])))

(defn modal-with-action-range
  [title-txt button-txt send-key-fn input-spec & [input-type]]
  (let [key-from (r/atom nil)
        key-to   (r/atom nil)
        clicked  (r/atom false)
        on-click #(cond
                    @clicked
                    (js/alert "You clicked already!")

                    (not (s/valid? input-spec @key-from))
                    (js/alert (str (s/explain-str input-spec @key-from)))

                    (not (s/valid? input-spec @key-to))
                    (js/alert (str (s/explain-str input-spec @key-to)))

                    (not (<= @key-from @key-to))
                    (js/alert "`from` should be less than `to`")

                    (> (- @key-to @key-from) 5)
                    (js/alert "Max range of IDs is 5")

                    :else
                    (do
                      (reset! clicked true)
                      (send-key-fn @key-from @key-to)))]
    (fn []
      [modal
       :header [:div title-txt]
       :body   [:div
                [:input.input.m4
                 {:type input-type
                  :placeholder "ADS profile id (from)"
                  :on-key-press (fn [e]
                                    (when (= (.-key e) "Enter")
                                      (on-click)))

                  :on-change (fn [e]
                               (let [value (.. e -target -value)]
                                 (reset! clicked false)
                                 (reset! key-from value)))}]
                
                [:input.input.m4
                 {:type input-type
                  :placeholder "ADS profile id (to, included)"
                  :on-key-press (fn [e]
                                    (when (= (.-key e) "Enter")
                                      (on-click)))

                  :on-change (fn [e]
                               (let [value (.. e -target -value)]
                                 (reset! clicked false)
                                 (reset! key-to value)))}]]
       :footer [:div
                [:a.button.is-primary.is-small
                 {:on-click on-click}
                 button-txt]
                [:a.button.is-danger.is-small
                 {:on-click #(db/remove! :modal)}
                 "Close"]]])))

(defn the-warning-form [title result]
  (let []
    (fn []
      [modal
       :header [:div title]
       :body [:div result]
       :footer [:div
                [:a.button.is-danger.is-small
                 {:on-click #(db/remove! :modal)}
                 "Close"]]])))
