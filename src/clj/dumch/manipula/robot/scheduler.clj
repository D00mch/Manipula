(ns dumch.manipula.robot.scheduler
  (:require
   [dumch.manipula.robot.action-util :as action]
   [dumch.manipula.robot.color :as color]
   [integrant.core :as ig]
   [robot.core :as r])
  (:import
   [java.util.concurrent ScheduledThreadPoolExecutor ThreadFactory TimeUnit]))

(defn uuid [] (.toString (java.util.UUID/randomUUID)))

(defn- random-thread-name [prefix]
  (str prefix "-" (uuid)))

(defn- thread-factory [thread-name-prefix]
  (proxy [ThreadFactory] []
    (newThread [thunk]
      (Thread. thunk (random-thread-name thread-name-prefix)))))

(defn- scheduled-executor [pool-size thread-name-prefix]
  (->> thread-name-prefix
       thread-factory
       (ScheduledThreadPoolExecutor. pool-size)))

(def ^:private scheduled-thread-pool-size (.availableProcessors (Runtime/getRuntime)))

(defonce SCHEDULED-EXECUTOR (scheduled-executor
                             scheduled-thread-pool-size
                             "STREAM-SCHEDULER"))

(defn- safe-fn [thunk descriptor]
  #(try
     (thunk)
     (catch Exception e
       (println e (str "Error periodically executing " descriptor)))))

(defn run-periodically! [descriptor time-period-millis thunk]
  (.scheduleAtFixedRate SCHEDULED-EXECUTOR
                        (safe-fn thunk descriptor)
                        time-period-millis
                        time-period-millis
                        TimeUnit/MILLISECONDS))

(defn cancel! [scheduled-future]
  (.cancel scheduled-future true))

(def mouse-clients (atom {}))

(defn send-mouse-info-to-mouse-clients []
  (doseq [[client-id send-fn] @mouse-clients
          :let [rgb (r/pixel-argb)
                active-app (action/active-app-name)]]
    (send-fn client-id [:dev/mouse-stream
                        {:xy    (r/mouse-pos)
                         :color (color/argb->char rgb)
                         :rgb   (into {} rgb)
                         :app   active-app}])))

(defmethod ig/init-key :data/mouse-stream [_ config]
  (run-periodically! "Mouse Stream" 1000 #'send-mouse-info-to-mouse-clients))

(defmethod ig/halt-key! :data/mouse-stream [_ scheduled-future]
  (cancel! scheduled-future)
  (reset! mouse-clients {}))

(comment

  (def atm (atom nil))
  (swap! mouse-clients assoc :abc (fn [_ d] (swap! atm conj d)))
  (swap! mouse-clients dissoc :abc)
  ;;
  )
