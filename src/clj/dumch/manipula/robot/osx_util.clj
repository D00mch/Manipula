(ns dumch.manipula.robot.osx-util
  (:require
   [clojure.java.shell :refer [sh]]
   [clojure.string :as str]))

(defn get-active-app-name []
  (let [result (sh "osascript" "-e" 
                   "tell application \"System Events\" to get the name of every process whose frontmost is true")]
    (str/trim-newline (:out result))))
