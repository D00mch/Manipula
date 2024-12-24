(ns dumch.manipula.util.resources
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [dumch.manipula.util.util :as util]
   [sci.core :as sci]))

(defonce sci-ctx
  (sci/init {:async? true
             :disable-arity-checks true
             :classes {:allow :all}}))

(defn get-std-path []
  (if util/win?
    "C:\\Users\\Administrator\\Desktop\\manipula\\resources\\public\\automation\\common\\stdlib.edn"
    "resources/public/automation/common/stdlib.edn"))

(defn get-automation-path []
  (if util/win?
    "C:\\Users\\Administrator\\Desktop\\manipula\\resources\\public\\automation"
    "resources/public/automation/"))

(defn get-private-widget-path []
  (str (if util/win?
         "C:\\Users\\Administrator\\Desktop\\manipula\\resources\\private\\automation\\"
         "resources/private/automation/")
       "widget_mm.edn"))

(defn get-private-widget-password-path []
  (str (if util/win?
         "C:\\Users\\Administrator\\Desktop\\manipula\\resources\\private\\automation\\"
         "resources/private/automation/")
       "widget_mm_pass.edn"))

(def clear-windows-path #(str/replace % "\\" "/"))

(defn automation-filenames []
  (let [directory (io/file (get-automation-path))]
    (->> (.listFiles directory)
         (map str)
         (map clear-windows-path))))

(def apply-std-delay
  (-> (get-std-path)
      slurp
      (sci/eval-string sci-ctx)
      delay))

(defn slurp-with-std-eval [path]
  (force apply-std-delay)
  (-> path
      slurp
      (sci/eval-string sci-ctx)))

(defn mm-seed-login-actions []
  (slurp-with-std-eval
   (get-private-widget-path)))

(defn mm-pass-login-actions []
  (slurp-with-std-eval
   (get-private-widget-password-path)))
