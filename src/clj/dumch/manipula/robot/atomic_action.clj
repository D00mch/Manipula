(ns dumch.manipula.robot.atomic-action
  (:require
   [clojure.spec.alpha :as s]
   [clojure.tools.logging :as log]
   [clojure.walk :as walk]
   [dumch.manipula.flat-actions :as flat-actions]
   [dumch.manipula.robot.action-util :as action-util]
   [dumch.manipula.robot.color :as color]
   [dumch.manipula.scenario-util :as scenario-util]
   [dumch.manipula.types :as types]
   [dumch.manipula.util.util :as util]
   [flatland.ordered.map :refer [ordered-map]]
   [robot.core :as r]))

(defmulti check! :type)

(defn wrap-name [check check-fn]
  (try
    (check-fn)
    (catch clojure.lang.ExceptionInfo e
      (throw
       (ex-info "Wrong pattern" (assoc (ex-data e)
                                       :cause (str e)
                                       :check check))))))

(defmethod check! :opened!
  [{regexp :regexp, wait :await :or {wait 2000} :as msg}]
  (wrap-name msg #(action-util/await-expected-url wait (re-pattern regexp))))

(defn- pattern-check
  [{:keys [regexp length direction], [x y] :xy, wait :await
    :or   {wait 2000} :as atomic-check}
   cond-map-fn]
  (let [line-f (case direction
                 :ver color/ver-line-str
                 :hor color/hor-line-str)
        line-f #(line-f x y length)
        cond-f #(->> (line-f)
                     (re-find (re-pattern regexp))
                     boolean
                     cond-map-fn)]
    (wrap-name atomic-check
               #(action-util/wait-condition wait line-f cond-f))))

(defmethod check! :pattern [atomic-check]
  (pattern-check atomic-check identity))

(defmethod check! :!pattern [atomic-check]
  (pattern-check atomic-check not))

(defmethod check! :lang-eng [msg]
  (let [en? (if util/linux?
              (->> ["bash" "-c" "setxkbmap -query | grep layout"]
                   action-util/linux-shell
                   (re-matches #".*(us|eng|en)$"))

              (->> ^java.util.Locale (r/get-current-layout)
                   .getDisplayName
                   (re-matches #".*English.*")))]
    (when-not en?
      (throw (ex-info "Wrong layout" {:reason :system-wrong-layout
                                      :actual (.getDisplayName (r/get-current-layout))
                                      :check   msg})))))

(defmethod check! :focused [{regexp :regexp :as msg}]
  (let [app-name-fn action-util/active-app-name]
    (when-not (re-find (re-pattern regexp) (app-name-fn))
      (throw (ex-info "Wrong app name"
                      {:reason :system-wrong-app
                       :actual (app-name-fn)
                       :check   msg})))))

(defmethod check! :screen-size [{xy :xy :as msg}]
  (when-not (= (r/get-screen-size) xy)
    (throw (ex-info "Wrong resolution" {:reason :wrong-resolution
                                        :actual (r/get-screen-size)
                                        :check  msg}))))

(defn perform-check! [check]
  (when check
    (let [[conformed] (s/conform :action/check check)
          checks      (case conformed
                        :one [check]
                        :many check)]
      (doseq [c checks]
        (check! c)))))

(defn perform-atomic!
  [{data :atomic/data :as action}]
  (case (:atomic/type action)
    :atomic/wait     (r/sleep data)
    :atomic/check    (perform-check! (:check/check data))
    :atomic/repeat   (perform-check! (:repeat/check data))
    :atomic/group    true
    :clipboard/put   (r/clipboard-put! data)
    :keyboard/key    (r/type! data)
    :keyboard/hotkey (r/hot-keys! data)
    :keyboard/text   (r/type-text! data)
    :mm/seed         (action-util/enter-key-metamask data)
    :mouse/scroll    (r/scroll! data)
    :mouse/move      (action-util/mouse-move! data)
    :mouse/click     (r/mouse-click! data)))

(def ^:dynamic history (atom {}))

#_{"Stargaet, step 1"
   {:result :success
    :incidents [{:expected true
                 :safe true
                 :type :keyboard/key
                 :data :cmd}]}}

(defn perform-atomic-record!
  "Returns `true` if performed without errors.
  Errors could be thrown on checks and repeats."
  [{-type :atomic/type
    {-name :action/name} :atomic/meta
    {times :repeat/times :as data} :atomic/data :as atomic}]
  (try
    (perform-atomic! atomic)
    (swap! history assoc-in [-name :result] :success)
    true
    (catch Throwable e
      (let [safe? (or (:check/safe data)
                      (and (= -type :atomic/repeat)
                           (> times 0)))]
        (swap! history
               update-in
               [-name :incidentes]
               conj
               {:expected (= (class e) clojure.lang.ExceptionInfo)
                :safe safe?
                :type -type
                :data data})
        (if safe?
          false
          (do (swap! history assoc-in [-name :result] :fail)
              (throw
               (ex-info (str e)
                        (assoc (ex-data e)
                               :action-name -name)))))))))

(defn index-of [match? coll]
  (let [idx? (fn [i a] (when (match? a) i))]
    (first (keep-indexed idx? coll))))

(defn find-step-by-name [-name atomics]
  (index-of #(= (-> % :atomic/meta :action/name) -name)
            atomics))

(defn- traverse-atomics
  "Performs all the actions synchronously, given:
  `step` number to start with, vector of `atomics`, and `halt?` atom,"
  [step atomics halt?]
  (when @halt?
    (throw (ex-info "Stopped by user!" {:reason :stopped})))
  (when (< step (count atomics))
    (let [{-type :atomic/type
           {times :repeat/times} :atomic/data
           {jump-diff :jump/diff} :atomic/jump :as atomic}
          (nth atomics step)]

      (when (and (= -type :atomic/repeat)
                 (not (perform-atomic-record! atomic)))
        ;; inner repeat loop
        (traverse-atomics
         (+ step jump-diff)
         (-> atomics
             (update-in [step :atomic/data :repeat/times] dec)
             (subvec 0 (inc step)))
         halt?))

      (cond
        (and (= -type :atomic/check)
             jump-diff ;; inherited from parent group 'safe' flag
             (not (perform-atomic-record!
                   (assoc-in atomic [:atomic/data :check/safe] true))))
        (recur (+ step jump-diff) atomics halt?)

        :else
        (do
          (when-not (= -type :atomic/repeat) (perform-atomic-record! atomic))
          (recur (inc step) atomics halt?))))))

(defn- compose-history [actions history]
  (walk/prewalk
   (fn [node]
     (if-let [{-name :name actions :actions} (and (:name node) node)]
       (cond-> {-name (get-in history [-name :result] :skipped)}
         (get-in history [-name :incidentes])
         (assoc :incidents (get-in history [-name :incidentes]))

         actions
         (assoc :actions actions))
       node))
   actions))

(defn perform-with-continuation! [{:keys [actions step]} halt? update-fn]
  (binding [history (atom (ordered-map))]
    (when-not @halt?
      (let [names      (scenario-util/actions->steps actions)
            step->name (into {} (map (fn [[a b]] [b a])) names)
            name->step (into {} names)
            the-name   (get step->name step)
            atomics    (flat-actions/decompose-action actions)]
        (try
          (when update-fn
            (add-watch
             history
             :result-stream
             (fn [_ _ old-value new-value]
               (when (not= new-value old-value)
                 (update-fn (last new-value))))))
          (traverse-atomics (find-step-by-name the-name atomics) atomics halt?)
          {:result :success
           :history (compose-history actions (deref history))
           :last-action (last @history)}
          (catch Throwable e
            (let [data
                  (ex-data e)

                  data
                  (-> (ex-data e)
                      (assoc :last-action (last @history))
                      (assoc :history (compose-history actions (deref history)))
                      (assoc :step-number (name->step (:action-name data))))]

              (throw
               (ex-info (str e) data))))
          (finally
            (remove-watch history :result-stream)))))))

(defn perform-with-continuation-check-input!
  [{:keys [actions step]} halt? & [update-fn]]
  (let [input     (scenario-util/extend-actions actions)
        conformed (s/conform ::types/input input)]
    (if (s/invalid? conformed)
      (throw (ex-info "Invalid input"
                      {:reason (s/explain-str ::types/input input)}))
      (perform-with-continuation!
       {:actions (case (first conformed)
                   :action [input]
                   :aciton-seq input)
        :step step}
       halt?
       update-fn))))

(comment

  (perform-with-continuation-check-input!
   {:actions
    [{:type :keyboard, :name "k", :keys [[:cmd :space]]}
     {:type :keyboard, :name "chrome" :keys ["google chrome" :enter]}

     {:type :group
      :name "neovide group"
      :actions [{:name "k"}, {:name "chrome"},
                {:name "scroll", :type :scroll :diff 50}]
      :repeat-unless {:check {:type :opened!
                              :regexp "ya.ru"}
                      :times 1}}]

    :step    0}
   (atom nil))

  (perform-atomic! {:atomic/type :keyboard/key,
                    :atomic/data :space})

  (perform-atomic! #:atomic{:type :atomic/check,
                            :data {:type :pattern
                                   :xy [0 0]
                                   :direction :ver
                                   :regexp "s{12}"
                                   :length 200},
                            :meta #:action{:name "test", :safe false}})
;;
  )


