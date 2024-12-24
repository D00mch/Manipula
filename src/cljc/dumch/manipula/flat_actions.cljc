(ns dumch.manipula.flat-actions 
  (:require
   [clojure.spec.alpha :as s]))

;; take high order action -> return list of atomic actions 
(defmulti decompose-action :type)

(defn- atomic-wait [-time]
  {:atomic/type :atomic/wait,
   :atomic/data (or -time 100)})

(defn- ->atomic [k v]
  {:atomic/type k, :atomic/data v})

(defn- atomic-check [check safe diff]
  (cond-> {:atomic/type :atomic/check
           :atomic/data {:check/safe (boolean safe)
                         :check/check check}}
    safe (assoc :atomic/jump {:jump/diff diff})))

(defn- atomic-repeat [-repeat -name]
  {:atomic/type :atomic/repeat
   :atomic/data {:repeat/check (:check -repeat)
                 :repeat/times (:times -repeat)
                 :repeat/name  -name}
   :atomic/meta {:action/name -name}})

(defn- assoc-atomic-meta [action atomic]
  (assoc atomic :atomic/meta
         {:action/name (:name action)
          :action/safe (boolean (:safe action))}))

(defn- with-default-start
  "Add atomic/delay and atomic/check for atomic-actions if needed"
  [{:keys [wait check safe regroup] -name :name} atomics]
  (cond->> atomics
    check (cons (atomic-check check safe (inc (count atomics))))
    regroup (cons (atomic-repeat regroup -name))
    true (cons (atomic-wait wait))))

(defn- decompose-keyboard
  "Get type and key, return atomic action(s) "
  [[-type data]]
  (case -type
    :mouse  (->atomic :mouse/click data)
    :xy     (->atomic :mouse/move data)
    :key    (->atomic :keyboard/key data)
    :number (atomic-wait data)
    :text   (->atomic :keyboard/text data)
    :key-n  (let [{k :key t :times} data]
              (map #(->atomic :keyboard/key %) (repeat t k)))
    :keys-n (let [{k :key t :times} data]
              (map #(->atomic :keyboard/hotkey %) (repeat t k)))
    :hotkey (->atomic :keyboard/hotkey data)
    :clpbrd (->atomic :clipboard/put (:text data))))

(defmethod decompose-action :keyboard
  [{ks :keys,  -delay :delay :or {-delay 300} :as action}]
  (let [atomics (->> (s/conform :action/keys ks)
                     (map decompose-keyboard)
                     flatten)
        delays  (repeat (atomic-wait -delay))]
    (->> (interleave atomics delays)
         drop-last
         (with-default-start action)
         (mapv #(assoc-atomic-meta action %)))))

(defmethod decompose-action :to-clipboard
  [{text :text :as action}]
  (->> [(->atomic :clipboard/put text)]
       (with-default-start action)
       (mapv #(assoc-atomic-meta action %))))

(defmethod decompose-action :scroll
  [{diff :diff :as action}]
  (->> [(->atomic :mouse/scroll diff)]
       (with-default-start action)
       (mapv #(assoc-atomic-meta action %))))

(defmethod decompose-action :mouse-move
 [{:keys [xy] :as action}]
 (->> [(->atomic :mouse/move xy)]
      (with-default-start action)
      (mapv #(assoc-atomic-meta action %))))

(defmethod decompose-action :mouse-rifle
  [{:keys [xy length direction step] :as action}]
  (let [[x y]  xy
        step   (or step 5)
        coords (case direction
                 :hor (->> (range x (+ 1 x length) step)
                           (map (fn [x] [x y])))
                 :ver (->> (range y (+ 1 y length) step)
                           (map (fn [y] [x y]))))

        clicks (for [[x y] coords]
                 [(->atomic :mouse/move [x y 10])
                  (->atomic :mouse/click :mouse1)])]
    (->> (mapcat identity clicks)
         (with-default-start action)
         (mapv #(assoc-atomic-meta action %)))))

(defmethod decompose-action :click
  [{:keys [xy] :as action}]
  (->> [(->atomic :mouse/move xy)
        (->atomic :mouse/click :mouse1)]
       (with-default-start action)
       (mapv #(assoc-atomic-meta action %))))

(defmethod decompose-action :insert-mm-seed
  [{:keys [seed] :as action}]
  (->> [(->atomic :mm/seed seed)]
       (with-default-start action)
       (mapv #(assoc-atomic-meta action %))))

(defn- assoc-atomic-jump
  "Adds `:atomic/jump`, doesn't override existing jumps"
  [diff atomic]
  (cond-> atomic
    (not (:atomic/jump atomic))
    (assoc :atomic/jump {:jump/diff diff})))

(defmethod decompose-action :group
  ;; meta is set up by actions itself mostly
  [{:keys [actions safe wait check]
    -name :name -repeat :repeat-unless :as action}]
  (let [atomics (->> actions
                     (map decompose-action)
                     (mapcat identity))
        atomics (cond->> atomics
                  check  (cons
                          (assoc-atomic-meta
                           action
                           (atomic-check check
                                         safe
                                         (+ (count atomics)
                                            (if -repeat 2 1)))))

                  true   (cons
                          (assoc-atomic-meta action
                                             (atomic-wait wait))))
        atomics (map-indexed (fn [i {-type :atomic/type :as atomic}]
                               (if (= -type :atomic/repeat)
                                 (assoc-atomic-jump (- i) atomic)
                                 atomic))
                             atomics)
        atomics (cond-> (vec atomics)
                  -repeat (conj
                           (assoc-atomic-jump
                            (- (count atomics))
                            (atomic-repeat -repeat -name))))]
    (cond->> atomics
      safe (map-indexed
            (fn [i a]
              (assoc-atomic-jump (- (count atomics) i)
                                 a))))))

(defmethod decompose-action :default [actions]
  (vec (mapcat identity (map decompose-action actions))))

(defn merge-waits
  "Useful for visualization. Do not use it to wrap atomics
   before evaluation, as `jumps` will be messed up"
  [actions]
  (letfn [(merge-if-wait
            [acc {-type :atomic/type data :atomic/data :as current}]
            (if (= -type :atomic/wait (:atomic/type (first acc)))
              (cons (update (first acc) :atomic/data + data)
                    (rest acc))
              (cons current acc)))]
    (reverse
     (reduce merge-if-wait [] actions))))

(comment
  (require '[dumch.manipula.scenario-util :as scenario-util])

  (decompose-action
   (scenario-util/extend-actions
    [{:type :group
      :name "Group1"
      :actions [{:type    :group
                 :name    "Group2"
                 :actions
                 [{:name "mouse", :type :mouse-move, :xy [1 1]}]}
                {:name "open spotlight"},
                {:name "select chrome"}]}

     {:type :keyboard, :name "open spotlight", :keys [[:cmd :space]]}
     {:type :keyboard, :name "select chrome",  :keys ["chrome" :enter]}]))

  (decompose-action
   [{:type :keyboard, :wait 150, :name "test2", :delay 10,
     :keys [300 [10 20] :mouse1 150], :safe true
     :check {:type :opened! :regexp "ya.ru"}}
    {:type :click, :wait 150 :name "click", :xy [10 10]}])

  (decompose-action
   (scenario-util/extend-actions
    [{:type :group
      :safe true
      :name "Group1"
      :actions [{:type          :group
                 :name          "Group2"
                 :check         {:type :screen-size
                                 :xy   [1024 960]}
                 :actions       [{:name "mouse", :type :mouse-move, :xy [100 100]}]
                 :repeat-unless {:check {:type :screen-size
                                         :xy   [1024 960]}
                                 :times 2}}
                {:name "open spotlight"},
                {:name "select chrome"}]
      :repeat-unless {:check {:type :opened!
                              :regexp "ya.ru"}
                      :times 1}}
     {:type :keyboard, :name "open spotlight", :keys [[:cmd :space]]}
     {:type :keyboard, :name "select chrome",  :keys ["google chrome" :enter]
      :safe true}]))

;;
  )
