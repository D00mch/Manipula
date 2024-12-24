(ns dumch.manipula.core
  (:require
   [clojure.tools.logging :as log]
   [dumch.manipula.config :as config]
   [dumch.manipula.env :refer [defaults]] ;; Edges  
   [dumch.manipula.web.handler] ;; Routes
   [dumch.manipula.web.routes.api]
   [dumch.manipula.web.routes.pages]
   [dumch.manipula.web.routes.ws]
   [integrant.core :as ig]
   [kit.edge.server.undertow]
   #_[kit.edge.utils.nrepl])
  (:import (javax.swing JFrame JLabel)
           (java.awt.event ActionEvent))
  (:gen-class))

;; log uncaught exceptions in threads
(Thread/setDefaultUncaughtExceptionHandler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_ thread ex]
      (log/error {:what :uncaught-exception
                  :exception ex
                  :where (str "Uncaught exception on" (.getName thread))}))))

(defonce system (atom nil))

(defn stop-app []
  ((or (:stop defaults) (fn [])))
  (some-> (deref system) (ig/halt!))
  (shutdown-agents))

(defn start-app [& [params]]
  ((or (:start params) (:start defaults) (fn [])))
  (->> (config/system-config (or (:opts params) (:opts defaults) {}))
       (ig/prep)
       (ig/init)
       (reset! system))
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app)))

(defn create-window []
  (let [frame (JFrame. "Manipula")
        button (javax.swing.JButton. "Restart")]
    (.addActionListener button (proxy [java.awt.event.ActionListener] []
                                 (actionPerformed [^ActionEvent e]
                                   (ig/halt! @system)
                                   (->> (config/system-config (or (:opts defaults) {}))
                                        (ig/prep)
                                        (ig/init)
                                        (reset! system)))))
    (.add frame (JLabel. "  localhost:3000"))
    (.add frame button)
    (.setLayout frame (java.awt.FlowLayout.))
    (.setSize frame 250 100)
    (.setDefaultCloseOperation frame JFrame/EXIT_ON_CLOSE)
    (.setVisible frame true)))

(defn -main [& _]
  (start-app)
  (create-window))

(comment
  
  (require '[robot.core :as r])
  )
