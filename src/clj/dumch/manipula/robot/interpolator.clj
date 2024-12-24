(ns dumch.manipula.robot.interpolator)

;; Formulas http://inloop.github.io/interpolator/ 

(def ^:private tension (* 2.0 1.5))

(defn- first-half-fn [t s]
  (* t t (- (* (+ s 1) t) s)))

(defn- second-half-fn [t s]
  (* t t (+ (* (+ s 1) t) s)))

(defn anticipate-overshot [x]
  (if (< x 0.5)
    (* 0.5 (first-half-fn (* x 2.0) tension))
    (* 0.5 (+ (second-half-fn (- (* x 2.0) 2.0) tension) 2.0))))

(def ^:private factor 0.4)

(defn spring [x]
  (+ (* (Math/pow 2 (- (* 10 x)))
        (Math/sin (/ (* (- x (/ factor 4)) (* 2 Math/PI)) factor)))
     1))

(defn random []
  (if (= 1 (rand-int 2))
    anticipate-overshot
    spring))

