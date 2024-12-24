(ns dumch.manipula.robot.win-util
  (:require
   [clojure.java.shell :refer [sh]]
   [clojure.string :as str]))

(defn get-focused-app []
  (->> "C:\\Users\\Administrator\\Desktop\\manipula\\resources\\scripts\\get-window-title.ps1"
       (sh "powershell")
       :out
       str/split-lines
       last))

(comment

  (re-find #"SunBrowser" (get-focused-app))
  )
