(ns dumch.manipula.robot.color
  (:require
   [clojure.string :as str]
   [robot.core :as r]))

(defn orange? [{:keys [red green blue]}]
  (and (<= 200 red 255)
       (<= 85 green 180)
       (<= 0 blue 70)))

(defn blue? [{:keys [red green blue]}]
  (or (and (<= 0 red 170)
           (<= 0 green 215)
           (<= 120 blue 255)
           (<= red blue)
           (<= green blue))
      (<= (+ red green) blue)))

(defn grey? [{:keys [red green blue]}]
  (let [max-color (max red green blue)
        min-color (min red green blue)
        max-difference 30]
    (and (not= 255 red)
         (not= 255 blue)
         (not= 255 green)
         (<= 50 red 255)
         (<= 50 green 255)
         (<= 50 blue 255)
         (<= (- max-color min-color) max-difference))))

(defn green? [{:keys [red green blue]}]
  (or (and (<= 0 red 190)
           (<= 60 green 255)
           (<= 0 blue 130))
      (<= (+ red blue) green)))

(defn yellow? [{:keys [red green blue]}]
  (or (and (<= 120 red 255)
           (<= 175 green 255)
           (<= 0 blue 150)
           (not (> green (* 0.7 (+ red blue)))))
      (and (<= 0.9 (/ red (max green 1)) 1.1)
           (<= (* 4 blue) (+ green red)))))

(defn red? [{:keys [red green blue]}]
  (or (and (<= 128 red 255)
           (<= green red)
           (<= blue red))
      (<= (+ green blue) red)))

(defn violet? [{:keys [red green blue]}]
  (and (<= 128 red 255)
       (<= 0 green 100)
       (<= 128 blue 255)))

(defn brown? [{:keys [red green blue]}]
  (and (<= 60 red 180)
       (<= 50 green 140)
       (<= 0 blue 65)))

(defn white? [{:keys [red green blue]}]
  (= 255 blue green red))

(defn black? [{:keys [red green blue]}]
  (= 0 blue green red))

(defn argb->char [color]
  (cond
    (white? color)  \w ;; should be first to win grey
    (black? color)  \d ;; dark
    (grey? color)   \s ;; s — silver, smoky
    (yellow? color) \y ;; RGB should be last in priority list
    (brown? color)  \c ;; coffee, chocolate
    (orange? color) \o
    (violet? color) \v
    (red? color)    \r
    (green? color)  \g
    (blue? color)   \b
    :else           \x))

(def int->char (comp argb->char r/int->argb))

(defn hor-line-str [x y width]
  (str/join (r/pixel-rgb-range-hor x y width int->char)))

(defn ver-line-str [x y height]
  (str/join (r/pixel-rgb-range-ver x y height int->char)))

(comment

  (do
    (println (r/pixel-argb))
    (argb->char (r/pixel-argb)))

  (do
    (println (r/pixel-argb))
    (green? (r/pixel-argb))))
