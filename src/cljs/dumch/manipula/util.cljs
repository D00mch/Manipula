(ns dumch.manipula.util
  (:require
   [ajax.core :as ajax]
   [clojure.spec.alpha :as s]
   [dumch.manipula.app-db :as db]
   [dumch.manipula.components.sci-ext :as sci-ext]
   [dumch.manipula.scenario-util :as scenario-util]
   [dumch.manipula.types :as types]
   [dumch.manipula.ws :as ws]
   [expound.alpha :as expound]
   [zprint.core :as zp])
  (:import
   [goog.async Debouncer]))

(defn play-sound [path] (.play (js/Audio. path)))

(defn check-file-exists [path]
  (ajax/GET path
    {:handler
     (fn [_]
       (js/console.log (str "File " path " exists.")))
     :error-handler
     (fn [_]
       (js/console.log (str "File " path " does not exist.")))}))

(defn debounce [f interval]
  (let [dbnc (Debouncer. f interval)]
    ;; We use apply here to support functions of various arities
    (fn [& args] (.apply (.-fire dbnc) dbnc (to-array args)))))

(defn coerce-clj-or-error! [input]
  (zp/zprint-file-str
   input
   nil
   {:parse-string? true
    :map {:force-nl? true
          :indent 0
          :sort? true
          :sort-in-code? true
          :key-order
          [:name :type
           :await :delay :diff :direction :length :regexp :url :xy :wait :keys
           :safe :check
           :actions :repeat-unless]
          :comma? false}
    :vector {:wrap? false}}))

#_(coerce-clj-or-error!
  "{:type :keyboard,,, :name \"keyboard\"
 :check {:type :pattern :xy [10 10] :regexp \"\" :direction :ver :length 60 :await 10000}
 :keys [[:cmd :t] \"ya.ru\" (str (+ 5 (rand 1)))]
 :delay 1000}"
  )

(defn compile [input & {safe? :safe?}]
  (try
    (let [compiled-scenario (sci-ext/eval-string (coerce-clj-or-error! input))
          compiled-scenario (if (map? compiled-scenario)
                              [compiled-scenario]
                              compiled-scenario)
          extended-scenario (scenario-util/extend-actions compiled-scenario)
          conformed-actions (s/conform ::types/input extended-scenario)]
      (if (s/invalid? conformed-actions)
        {:error (expound/expound-str ::types/input extended-scenario)}
        {:result extended-scenario}))
    (catch js/Error e
      (if safe?
        {:error (str e)}
        (throw e)))))

(defn save-editor-scenario! [input path]
  (try
    (let [{extended-scenario :result, error :error} (compile input)]
      (if error
        (db/assoc-in! [path :compilation :error] {:error (str error)})
        (do
          (db/assoc-in! [path :compilation :error] nil)
          (db/assoc-in! [path :compilation :result] extended-scenario)
          (db/assoc-in! [path :compilation :name->step]
                        (->> extended-scenario
                             scenario-util/extend-actions
                             scenario-util/actions->steps
                             (into {}))))))
    (catch js/Error e
      (db/assoc-in! [path :compilation :result] nil)
      (db/assoc-in! [path :compilation :error] {:error (str e)}))))

(defn start-scenario [path actions step]
  (db/assoc-in! [path :execution :result] "Running...")
  (db/assoc-in! [path :execution :error] nil)
  (ws/send-message!
   [(keyword (str (subs (str path) 1) "/action")) ;; :dev/action | :main/action
    {:actions actions
     :step    step}]))

(comment
  (check-file-exists "/sound/done.wav")

  (check-file-exists "/css/screen.css")
  ;
  )
