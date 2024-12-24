(ns dumch.manipula.robot.action-util
  (:require
   [clojure.string :as str]
   [dumch.manipula.robot.interpolator :as interpolator]
   [dumch.manipula.robot.osx-util :as osx-util]
   [dumch.manipula.robot.win-util :as win-util]
   [dumch.manipula.util.util :as util]
   [robot.core :as r])
  (:import
   [java.io BufferedReader InputStreamReader]
   [java.lang Runtime]))

(defn enter-key [separator-action end-action phrase]
  (let [words (str/split phrase #" ")
        xy    [100 100]]
    (doseq [word words]
      (r/mouse-move! xy)
      (r/sleep 50)
      (r/mouse-move! xy)
      (r/type-text! word)
      (r/mouse-move! xy)
      (separator-action))
    (r/sleep 50)
    (end-action)))

(def enter-key-metamask
  (partial enter-key
           (fn [] (r/type! :tab) (r/type! :tab))
           (fn [] (r/type! :enter))))

(defn launch-fullscreen [app-name]
  (.exec (Runtime/getRuntime)
         (str "cmd /c start " app-name))
  (r/sleep 3000)
  (r/hot-keys! [:win :up]))

(defn repeat-key [k times -delay]
  (println :key k)
  (dotimes [n times]
    (r/type! k)
    (r/sleep -delay)))

(defn type! [k]
  (case k
    (:mouse1 :mouse2 :mouse3) (r/mouse-click! k)
    (r/type! k)))

(defn- calc-checks-count
  "Given the whole delay `x`, return the number of tryies"
  [x]
  (cond
    (< x 1000)    (Math/log10 x)
    (< x 10000)   (+ 5 (Math/log10 (- x 100)))
    (< x 100000)  (+ 10 (Math/log10 (- x 1000)))
    (< x 1000000) (+ 20 (Math/log10 (- x 10000)))
    :else         (+ 40 (Math/log10 (- x 100000)))))

(defn- calc-try-delay [whole-delay]
  (let [whole-delay      (Math/max 500 whole-delay)
         await-count-left (int (calc-checks-count whole-delay))
         try-delay        (quot whole-delay await-count-left)]
     [await-count-left try-delay]))

(defn wait-condition
  ([whole-delay expected-value-fn cond-fn]
   (let [[await-count-left try-delay] (calc-try-delay whole-delay)]
     (wait-condition try-delay await-count-left  expected-value-fn cond-fn)))
  ([try-delay await-count-left expected-value-fn cond-fn]
   (when (> try-delay 0) (r/sleep try-delay))
   (cond (cond-fn)
         :ok
         (= await-count-left 0)
         (throw (ex-info "Can't wait any longer" {:actual (expected-value-fn)}))
         :else
         (recur try-delay (dec await-count-left) expected-value-fn cond-fn))))

(def hotkey-go-to-url [(if util/osx? :cmd :ctrl) :l])

(def hotkey-copy [(if util/osx? :cmd :ctrl) :c])

(defn put-url-to-clipboard! []
  (r/clipboard-put! "") ;; be sure to clean the previous result
  (r/sleep 70)
  (r/hot-keys! hotkey-go-to-url)
  (r/sleep 140)
  (r/hot-keys! hotkey-copy))

(defn await-expected-url [whole-delay url-regex]
  (let [cond-fn
        (fn []
          (put-url-to-clipboard!)
          (r/sleep 70)
          (re-find url-regex (or (r/clipboard-get-string) "")))

        [await-count-left try-delay]
        (calc-try-delay whole-delay)

        fn-time
        300

        try-delay
        (Math/max (- try-delay fn-time) 0)
        
        await-count-left
        (Math/min (quot whole-delay fn-time)
                  await-count-left)]
    (wait-condition try-delay await-count-left #(r/clipboard-get-string) cond-fn)))

(comment
  (do (put-url-to-clipboard!)
      (r/clipboard-get-string))
  )

;; complex mouse aciton

(defn mouse-move!
  "Move mouse like human"
  ([[x y steps]]
   (mouse-move! x y (or steps 100)))
  ([x y steps]
   (mouse-move! x y (interpolator/random) steps))
  ([x y interpolator steps]
   (let [[current-x current-y] (r/mouse-pos)
         dx (- x current-x)
         dy (- y current-y)]
     (dotimes [i steps]
       (let [t             (/ i steps)
             interpolation (interpolator t)
             step-x        (+ current-x (* dx interpolation))
             step-y        (+ current-y (* dy interpolation))]
         (Thread/sleep (long (rand 20)))
         (r/mouse-move! step-x step-y))))))

(comment
  (mouse-move! 1131 48 interpolator/anticipate-overshot 100)
  (mouse-move! 220 477 interpolator/anticipate-overshot 100))

(defn move-mouse-in-circle!
  "Human useless behaviour"
  ([]
   (move-mouse-in-circle! (+ 1 (rand-int 3))))
  ([circles]
   (let [mp (r/mouse-pos)]
     (move-mouse-in-circle! mp 100 (+ 5 (rand 15)) circles)))
  ([mouse-pos steps radius circles]
   (move-mouse-in-circle! mouse-pos steps radius circles #(interpolator/random)))
  ([mouse-pos steps radius circles interpolator-fn]
   (let [[center-x center-y] mouse-pos
         step-size (/ (* 2 Math/PI) steps)]
     (doseq [_ (range circles)
             :let [interpolator (interpolator-fn)]]
       (dotimes [i steps]
         (let [angle         (* i step-size)
               t             (/ i steps)
               interpolation (interpolator t)
               x             (+ center-x (* interpolation (* radius (Math/sin angle))))
               y             (+ center-y (* interpolation (* radius (Math/cos angle))))]
           (r/mouse-move! (int x) (int y))
           (r/sleep 4))))
     (mouse-move! mouse-pos))))

(defn linux-shell [cmd]
  (let [process (if (string? cmd)
                  (.exec (Runtime/getRuntime) ^String cmd)
                  (.exec (Runtime/getRuntime) ^"[Ljava.lang.String;" (into-array cmd))) ;]
        input-stream (.getInputStream process)
        reader (BufferedReader. (InputStreamReader. input-stream "UTF-8"))]
    (.readLine reader)))

(def active-app-name
  (cond
    util/linux? #(linux-shell "xdotool getwindowfocus getwindowname")
    util/win?   win-util/get-focused-app
    util/osx?   osx-util/get-active-app-name))

(comment

  (linux-shell "xdotool getwindowfocus getwindowname")
  (linux-shell ["bash" "-c" "setxkbmap -query | grep layout"])
  ;;
  )
