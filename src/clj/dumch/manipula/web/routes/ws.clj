(ns dumch.manipula.web.routes.ws
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [dumch.manipula.robot.atomic-action :as action]
   [dumch.manipula.robot.color :as color]
   [dumch.manipula.robot.scheduler :refer [mouse-clients]]
   [dumch.manipula.util.resources :as resources]
   [integrant.core :as ig]
   [ring.middleware.keyword-params :as keyword-params]
   [ring.middleware.params :as params]
   [robot.core :as r]
   [taoensso.sente :as sente]
   [taoensso.sente.server-adapters.undertow :refer [get-sch-adapter]]))

(defn user-id-fn [ring-req]
  (get-in ring-req [:params :client-id]))

(defmulti on-message :id)

(defmethod on-message :default
  [{:keys [id client-id ?data] :as message}]
  (println "on-message: id: " id "  cilient-id: " client-id " ?data: " ?data))

(defn- clean-died-clients [clients-map active-set]
  (->> clients-map
       (filter (fn [[k _]] (active-set k)))
       (into {})))

(defmethod on-message :dev/mouse-stream
  [{:keys [id client-id ?data send-fn connected-uids] :as message}]
  (swap! mouse-clients clean-died-clients (-> @connected-uids :ws))
  (if ?data
    (swap! mouse-clients assoc client-id send-fn)
    (swap! mouse-clients dissoc client-id)))

(defmethod on-message :dev/pattern-request
  [{:keys [id client-id send-fn]
    {:keys [length direction] :as ?data} :?data}]
  (let [[x y :as xy] (r/mouse-pos)
        _            (r/mouse-move! 0 0)
        _            (r/sleep 500)
        line-fn      (case direction
                       :ver color/ver-line-str
                       :hor color/hor-line-str)
        pattern      (line-fn x y length)]
    (send-fn client-id [id {:xy xy, :pattern pattern}])))

(def halt? (atom false))

(defmethod on-message :scenario/halt! [_]
  (reset! halt? true))

(def running-atom (atom false))

(defn set-running-or-throw! []
  (when @running-atom
    (throw (ex-info "App is running currently, can't start new scenario"
                    {:reason "App is running currently, you probably clicked twise"})))
  (reset! running-atom true))

(defn eval-actions!
  [run-type update-run-type safe?
   {:keys [id client-id send-fn ?data]}
   & [on-success]]
  (future
    (try
      (set-running-or-throw!)
      (reset! halt? false)
      (let [perform-actions-fn
            (if safe?
              action/perform-with-continuation-check-input!
              action/perform-with-continuation!)

            update-fn
            (fn [update-result]
              (send-fn client-id [update-run-type update-result]))

            result
            (perform-actions-fn ?data halt? update-fn)]
        (send-fn client-id [id result])
        (when on-success (on-success)))
      (catch clojure.lang.ExceptionInfo e
        (log/error e {:run-type run-type :ex-data (ex-data e)})
        (send-fn client-id [id {:result :error
                                :error  (ex-data e)}]))
      (catch Throwable e
        (log/error e)
        (send-fn client-id [id {:result :error
                                :error  (str e)}]))
      (finally
        (reset! running-atom false)))))

(defmethod on-message :dev/action
  [{:keys [id client-id send-fn ?data] :as msg}]
  (eval-actions! :dev/action :dev/action-update true msg))

(defmethod on-message :main/action
  [{:keys [id client-id send-fn ?data] :as msg}]
  (eval-actions! :main/action :main/action-update true msg))

(defn coerce-id-profile [id]
  (log/info "coersing id-profile: " id)
  (cond
    (and (number? id) (neg? id))
    (throw (ex-info "Id profile can't be negative number" {}))

    (number? id)
    id

    :else
    (or (-> id str str/trim parse-long)
        (throw (ex-info "Can't parse id profile" {})))))

(defmethod on-message :main/scenario-list
  [{:keys [id client-id send-fn ?data] :as msg}]
  (let [file-names (resources/automation-filenames)]
    (send-fn client-id [id {:result file-names}])))

(defmethod on-message :main/scenario-get
  [{:keys [id client-id send-fn ?data] :as msg}]
  (try
    (let [file (slurp ?data)]
      (send-fn client-id [id {:result file}]))
    (catch Throwable e
      (log/error e "Can't read file with path:" ?data)
      (send-fn client-id [id {:result :error
                              :error (str e)}]))))

(defmethod  on-message :dev/stdlib
  [{:keys [id client-id send-fn ?data] :as msg}]
  (try
    (let [file (-> (resources/get-std-path) slurp)]
      (send-fn client-id [id {:result file}]))
    (catch Throwable e
      (log/error e "Can't read file with path:" ?data)
      (send-fn client-id [id {:result :error
                              :error (str e)}]))))

(defmethod ig/init-key :sente/connection
  [_ _]
  (sente/make-channel-socket!
   (get-sch-adapter)
   {:packer :edn
    ;; :csrf-token-fn nil
    :user-id-fn user-id-fn}))

(def tmp-msgs (atom nil))

(defn handle-message! [msg]
  ;; TODO - error handling
  (reset! tmp-msgs msg)
  (on-message msg))

(defmethod ig/init-key :sente/router
  [_ {:keys [connection machine-id mm-url api-url arg-url bra-url] :as opts}]
  (sente/start-chsk-router!
   (:ch-recv connection)
   (fn [msg]
     (#'handle-message! (assoc msg
                               :mm-url mm-url
                               :bra-url bra-url
                               :arg-url arg-url
                               :api-url api-url
                               :machine-id machine-id)))))

(defmethod ig/halt-key! :sente/router
  [_ stop-fn]
  (when stop-fn (stop-fn)))

(defn route-data
  [opts]
  (merge
   opts
   {:middleware
    [keyword-params/wrap-keyword-params
     params/wrap-params]}))

(defn ws-routes [{:keys [connection] :as opts}]
  [["" {:get  (:ajax-get-or-ws-handshake-fn connection)
        :post (:ajax-post-fn connection)}]])

(derive :reitit.routes/ws :reitit/routes)

(defmethod ig/init-key :reitit.routes/ws
  [_ {:keys [base-path]
      :or   {base-path ""}
      :as   opts}]
  [base-path (route-data opts) (ws-routes opts)])
