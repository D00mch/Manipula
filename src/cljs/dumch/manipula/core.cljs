(ns dumch.manipula.core
  (:require
   [dumch.manipula.app-db :as db]
   [dumch.manipula.components.common :as com]
   [dumch.manipula.components.editor :as editor]
   [dumch.manipula.components.sci-ext :as sci-ext]
   [dumch.manipula.pages.dev :as dev]
   [dumch.manipula.pages.diff :as diff]
   [dumch.manipula.pages.docs :as docs]
   [dumch.manipula.pages.main :as main]
   [dumch.manipula.util :as util]
   [dumch.manipula.ws :as ws]
   [reagent.dom :as d]))

;; -------------------------
;; Views

(defn modal []
  (when-let [session-modal (db/get :modal)]
    [session-modal]))

(defn home-page []
  [:div {:class "card-content"}
   [modal]
   [:div {:class "tabs is-centered"}
    [:ul
     (com/tab-page "Main" #(ws/send-message! [:main/scenario-list]))
     (com/tab-page "Dev")
     (com/tab-page "Docs")
     (com/tab-page "Diff")]]
   (case (db/get :selected-tab)
     "Main" [main/main-page]
     "Docs" [docs/docs-page]
     "Dev" [dev/dev-page]
     "Diff" [diff/diff-page])])

;; -------------------------
;; Initialize app

(defn ^:dev/after-load mount-root []
  (d/render [home-page] (.getElementById js/document "app")))

(defn- on-action [path {:keys [result error] :as data}]
  (db/assoc-in! [path :execution :error] nil)
  (when (= :error result)
    (db/assoc-in! [path :execution :error] (assoc error :error true))
    (db/assoc-in! [path :step-name] (-> error :reason :name))
    (db/assoc-in! [path :step] (-> error :step-number)))
  (db/assoc-in! [path :execution :result] data))

(defn handler [{[id {:keys [error result] :as data}] :?data}]
  (println :id id data)

  (case id
    :dev/mouse-stream
    (reset! dev/mouse-info data)

    :dev/pattern-request
    (do (db/assoc-in! [:dev :pattern] (-> data :pattern))
        (db/assoc-in! [:dev :pattern-xy] (-> data :xy)))

    :dev/action
    (do (on-action :dev data)
        (when (= :error result)
          (util/play-sound "/sound/warn.wav")))

    :dev/action-update
    (db/assoc-in! [:dev :execution :result]
                  {:running data})

    :main/action-update
    (db/assoc-in! [:main :execution :result]
                  {:running data})

    :main/action
    (do (on-action :main data)
        (when (= :error result)
          (util/play-sound "/sound/warn.wav")
          (db/assoc! :modal
                     (com/the-warning-form
                      "Scenario error"
                      [:pre {:style {:white-space "pre-wrap"}}
                       (util/coerce-clj-or-error!
                        (str (dissoc error :history)))]))))

    :main/scenario-list
    (do
      (ws/send-message! [:dev/stdlib true])
      (db/assoc-in! [:main :scenario :list] (or error (sort result))))

    :main/scenario-get
    (do
      (when-not error
        (db/assoc-in! [:main :scenario :selected] result)
        #_(db/assoc-in! [:main :execution :error] nil)
        ;; TODO: force editor to react on value change
        (editor/set-value-by-path! [:main :editor] result))
      (db/assoc-in! [:main :step] 0))

    :dev/stdlib
    (when-not error
      (try
        (sci-ext/eval-string result)
        (db/ws-assoc :stdlib result)
        (catch js/Error e
          (sci-ext/eval-string (db/ws-get :stdlib))
          (println "about to execute, error: " e))))
    nil))

(defn ^:export ^:dev/once init! []

  (ws/start-router! handler)
  (js/setTimeout #(ws/send-message! [:main/scenario-list]) 1000)
  (js/setTimeout #(ws/send-message! [:main/has-key?]) 200)
  (js/setTimeout #(ws/send-message! [:main/has-key?]) 1000)

  (mount-root))

(comment

  (ws/start-router! handler)

  (js/alert "Hi")
;;
  )

