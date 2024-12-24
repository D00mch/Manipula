(ns dumch.manipula.pages.main
  (:require
   [clojure.string :as str]
   [dumch.manipula.app-db :as db]
   [dumch.manipula.components.common :as com]
   [dumch.manipula.components.editor :as editor]
   [dumch.manipula.util :as util]
   [dumch.manipula.ws :as ws]))


(defn start-main-scenario []
  (util/start-scenario :main
                       (db/get-in [:main :compilation :result])
                       (db/get-in [:main :step])))

(defn main-details-area []
  [:div.m4
   [:p
    "Main workflow is finished with error"
    [:strong (str (db/get-in [:main :execution :error :reason :name]))]]

   [:div.columns.is-vcentered
    [:div.column.is-narrow
     [:p "Click \"Start\" to continue from the current step"]]]])

(defn editor-value []
  (editor/get-value-by-path [:main :editor]))

(def save-main-editor-scenario-debounced!
  (util/debounce (fn [_] (util/save-editor-scenario! (editor-value) :main)) 1000))

#_(db/get-in [:main :compiled-scenario])

(defn get-allow-login-button-class []
  (if (db/get-in [:main :the-key :result])
    "is-primary"
    "is-danger"))

(defn main-page []
  [:div
   [:div.columns
    [:div.column
     [:button.button.is-primary.is-small.m4
      {:on-click start-main-scenario}
      "Start!"]

     [:button.button.is-primary.is-small.is-danger.m4
      {:on-click #(ws/send-message! [:scenario/halt! {}])}
      "Stop!"]

     [:div.columns
      [:div.column.is-narrow
       [:p "Continue from step:"]]
      [:div.column
       [:p (str (db/get-in [:main :step]) ", "
                (db/get-in [:main :step-name]))]]]

     (when (db/get-in [:main :execution :error])
       [main-details-area])

     [com/result-area :main]

     [:div.content.m4
      [:span]
      [:h3 "Current scenario actions:"]
      (if-let [error (db/get-in [:main :compilation :error])]
        [:div.m4
         [:p "Scenarios compilation error:"]
         [:pre {:style {:white-space "pre-wrap"}}
          (str/replace (str error) "\\n" "\n")]]
        [com/actions-tree
         :actions (db/get-in [:main :compilation :result])
         :path :main])]]

    [:div.column.is-narrow
     [:p "Scenarios:"]
     (for [automation-file-name (db/get-in [:main :scenario :list])]
       ^{:key automation-file-name}
       [:div
        [:a
         {:on-click #(ws/send-message! [:main/scenario-get automation-file-name])}
         (last (re-find #".*/(.*)\.edn$" automation-file-name))]])]]

   (let [code (db/get-in [:main :scenario :selected])]
     [editor/editor
      :source (or code "[]")
      :editor-path [:main :editor]
      :on-change save-main-editor-scenario-debounced!])])
