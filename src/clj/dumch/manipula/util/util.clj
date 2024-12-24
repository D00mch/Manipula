(ns dumch.manipula.util.util
  (:require
   [clojure.string :as str]) 
  (:import
   (java.time Instant)
   (java.time ZoneId)
   (java.time.format DateTimeFormatter)))

(def osx? (.contains (.toLowerCase (System/getProperty "os.name")) "mac"))
(def linux? (.contains (.toLowerCase (System/getProperty "os.name")) "linux"))
(def win? (.contains (.toLowerCase (System/getProperty "os.name")) "windows"))

(defn rand-bool []
  (= 1 (rand-int 2)))

(defn uuid []
  (str (java.util.UUID/randomUUID)))

(defn millis-to-human [millis]
  (let [instant (Instant/ofEpochMilli millis)
        zone-id (ZoneId/systemDefault)
        formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss")]
    (.format (.atZone instant zone-id) formatter)))

(defn day-time-string []
  (millis-to-human (System/currentTimeMillis)))

(defn browser-url
  "Add 'chrome-extension://' prefix if needed; remove last '/'"
  [url]
  (let [url (if (str/ends-with? url "/")
              (subs url 0 (dec (count url)))
              url)]
    (if (str/starts-with? url "chrome-extension")
      url
      (str "chrome-extension://" url))))

