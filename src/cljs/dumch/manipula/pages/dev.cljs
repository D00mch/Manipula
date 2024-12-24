(ns dumch.manipula.pages.dev
  (:require
   [clojure.string :as str]
   [dumch.manipula.app-db :as db]
   [dumch.manipula.components.common :as com]
   [dumch.manipula.components.editor :as editor]
   [dumch.manipula.util :as util]
   [dumch.manipula.ws :as ws]
   [reagent.core :as r]))


(def mouse-info (r/atom {:xy [50 50] :color "?" :rgb [0 0 0]}))

(defn pattern-area []
  [:div.columns.is-vcentered
   [:div.column.is-narrow
    [:div.m4
     [com/dropdown-settings
      :name "Pattern direction"
      :state-path [:dev :pattern-direction]
      :values ["Vertical" "Horizontal"]]]
    [:div.m4
     [com/dropdown-settings
      :name "Pattern lenght"
      :state-path [:dev :pattern-length]
      :values (vec (range 30 300 30))]]
    [:div.m4
     [:button.button.is-primary.is-light.is-small.m4
      {:on-click #(js/setTimeout (fn []
                                   (ws/send-message!
                                    [:dev/pattern-request
                                     {:length    (db/get-in [:dev :pattern-length])
                                      :direction (case (db/get-in [:dev :pattern-direction])
                                                   "Vertical" :ver
                                                   "Horizontal" :hor)}])
                                   (util/play-sound "/sound/done.wav"))
                                 5000)}
      (str "Calculate pattern: "
           (case (db/get-in [:dev :pattern-direction])
             "Vertical" "top->bottom"
             "Horizontal" "left->right"))]]]
   [:div.column
    [:div.box (db/get-in [:dev :pattern])]
    [:div.box (str (db/get-in [:dev :pattern-xy]))]]])

(defn mouse-area []
  [:div.columns.is-vcentered
   [:div.column.is-narrow
    [:p "MouseXY: " (-> @mouse-info :xy str)]]
   [:div.column.is-narrow
    [:p "Color: " (-> @mouse-info :color str)]]
   [:div.column.is-narrow
    [:p "RGB: " (-> @mouse-info :rgb (select-keys [:red :green :blue]) str)]]
   
   [:div.column.is-narrow
    [:p "App: " (-> @mouse-info :app)]]])

(defn editor-value []
  (editor/get-value-by-path [:dev :editor]))

(defn put-dev-scenario-to-cookies! [_]
  (println "About to put scenario to cookies")
  (db/ws-assoc :dev-scenario (editor-value)))

(def put-dev-scenario-to-cookies-debounced!
  (util/debounce put-dev-scenario-to-cookies! 3500))

(def save-dev-editor-scenario-debounced!
  (util/debounce #(util/save-editor-scenario! (editor-value) :dev) 1000))

(defn start-dev-scenario []
  (if (empty? (db/get-in [:dev :selection]))
    (util/start-scenario :dev
                         (db/get-in [:dev :compilation :result])
                         (db/get-in [:dev :step]))
    (util/start-scenario :dev
                         (util/compile (db/get-in [:dev :selection]))
                         0)))

(defn start-dev-selection []
  (ws/send-message!
   [:dev/action
    {:actions (:result
               (util/compile
                (let [code (editor/get-selection [:dev :editor])]
                  (if (str/starts-with? code "{") ;;}
                    (str "[" code "]")
                    code))))
     :step    0}]))

(defn buttons-area []
  [:div.columns.is-vcentered.m4
   [:button.button.is-primary.is-small.m4
    {:on-click start-dev-scenario
     :disabled (db/get-in [:dev :compilation :error])}
    (str "Test from step: " (db/get-in [:dev :step]))]
   [:button.button.is-primary.is-small.m4
    {:on-click start-dev-selection}
    (str "Run selection")]
   [:button.button.is-primary.is-light.is-small.m4
    {:on-click #(editor/align-editor-by-path [:dev :editor])}
    "Align!"]
   [:button.button.is-primary.is-danger.is-small.m4
    {:on-click #(ws/send-message! [:scenario/halt! {}])}
    "Stop!"]

   [:div.dropdown.is-hoverable.is-up
    [:div.dropdown-trigger
     [:span.m4
      {:style {:marting 10}
       :aria-haspopup "true",
       :aria-controls "dropdown-menu4"}
      "Shortcuts hint"]]
    [:div.dropdown-menu
     {:id "dropdown-menu4", :role "menu" :style {:min-width 400}}
     [:div.dropdown-content
      [:div.dropdown-item
       [:p
        [:strong "Ctrl+l"]    " — ALign the code" [:br]
        [:strong "Ctrl+s"]    " — Send selection" [:br]
        [:strong "Ctrl+e"]    " — Fold the form" [:br]
        [:strong "Alt+Enter"] " — Eval the form" [:br]
        [:strong "Alt+Left"]  " — Jump to the left of the form" [:br]
        [:strong "Alt+Right"] " — Jump to the right of the form" [:br]
        [:strong "Alt+Down"]  " — Shrink selections" [:br]
        [:strong "Alt+Up"]    " — Grow selections" [:br]
        [:strong "Alt+s"]     " — Remove wrapping brackets" [:br]
        [:strong "Alt+b"]     " — Shink wrapping right" [:br]
        [:strong "Alt+k"]     " — Grow wrapping right" [:br]
        [:strong "\\"]        " — Autocomplete a form" [:br]]]]]]])

(defn dev-page []
  (r/with-let [_ (ws/send-message! [:dev/mouse-stream true])]
    [:div
     [pattern-area]
     [mouse-area]

     [editor/editor
      :source (db/ws-get :dev-scenario)
      :editor-path [:dev :editor]
      :on-change (fn [input]
                   (save-dev-editor-scenario-debounced! input)
                   (put-dev-scenario-to-cookies-debounced! input))
      :on-selection (fn [_]
                      (js/setTimeout start-dev-selection 300))
      :eval-result-path [:dev :eval]]

     [com/result-area :dev]

     [:div
      [buttons-area]
      [:div.container
       [:div {:style {:marginLeft 50}}
        [:span]
        [:h3 "Current scenario actions:"]
        (if-let [error (db/get-in [:dev :compilation :error])]
          [:div.m4
           [:p "Scenarios compilation error:"]
           [:pre {:style {:white-space "pre-wrap"}}
            (str/replace (str error) "\\n" "\n")]]
          [com/actions-tree
           :actions (db/get-in [:dev :compilation :result])
           :path :dev])]]]]
    (finally
      (ws/send-message! [:dev/mouse-stream false]))))

(comment
  (db/get-in [:dev :execution])
  ;;
  )
