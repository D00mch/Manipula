(ns dumch.manipula.types
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]))

(defmulti action-type :type)

(defmulti check-type :type)
(s/def :check/type keyword?)

(s/def :action/name string?)
(s/def :action/url string?) ;; could be regexp

(s/def ::coordinate (s/int-in 0 5000))

(s/def :action/xy (s/coll-of ::coordinate :kind vector? :count 2))
(s/def ::step (s/int-in 0 10000))
(s/def :action/regexp string?)
(s/def :action/direction #{:ver :hor})
(s/def :action/length ::coordinate)
(s/def :action/await (s/int-in 0 10000000))
(s/def :action/wait :action/await)
(s/def :action/delay (s/int-in 0 5000))
(s/def :action/safe boolean?)
(s/def :action/diff int?)
(s/def :action/times (s/int-in 0 100))
(s/def :action/text string?)
(s/def :action/checkpoint boolean?)
(s/def ::hotkey (s/coll-of keyword?))
(s/def ::key-repeat (s/cat :key keyword? :times :action/times))
(s/def ::hotkey-repeat (s/cat :key ::hotkey :times :action/times))
(s/def ::clipboard-put (s/cat :text :action/text))
(s/def ::mouse-click #{:mouse1 :mouse2 :mouse3})
(s/def :action/keys (s/coll-of (s/or :mouse  ::mouse-click
                                     :key    keyword?
                                     :key-n  ::key-repeat
                                     :keys-n ::hotkey-repeat
                                     :text   :action/text
                                     :number int?
                                     :xy     :action/xy
                                     :hotkey ::hotkey
                                     :clpbrd ::clipboard-put)))
(s/def ::check-core (s/keys :req-un [:check/type]
                            :opt-un [:action/await]))
(s/def :action/check  (s/or :one (s/multi-spec check-type :type)
                            :many (s/coll-of :action/check)))
(s/def :action/repeat-unless (s/keys :req-un [:action/check :action/times]))
(s/def :action/regroup :action/repeat-unless)
(s/def ::action-core
  (s/keys :req-un [:action/type :action/name]
          :opt-un [:action/check :action/wait :action/safe :action/regroup :action/checkpoint]))
(s/def :action/action (s/multi-spec action-type :type))
(s/def :action/actions (s/coll-of :action/action))

;;; ********** check types
;;;

(defmethod check-type :pattern [_]
    (s/and ::check-core
           (s/keys :req-un [:action/xy :action/length :action/regexp :action/direction])))

(defmethod check-type :!pattern  [_]
    (s/and ::check-core
           (s/keys :req-un [:action/xy :action/length :action/regexp :action/direction])))

(comment
  (s/valid? :action/check
            {:type :!pattern
             :xy   [1324 190]
             :length 160
             :direction :hor
             :await   3000
             :regexp "d{13,}x{17,}(s|x)*x{13,}d{4,}"}))

(defmethod check-type :opened!  [_]
  (s/and ::check-core (s/keys :req-un [:action/regexp])))

(defmethod check-type :screen-size  [_]
  (s/and ::check-core (s/keys :req-un [:action/xy])))

(defmethod check-type :lang-eng  [_]
  ::check-core)

(defmethod check-type :focused  [_]
  (s/and ::check-core (s/keys :req-un [:action/regexp])))

;;; ********** aciton types
;;;

(defmethod action-type :mouse-move  [_]
  (s/and ::action-core (s/keys :req-un [:action/xy])))

(defmethod action-type :click [_]
  (s/and ::action-core (s/keys :req-un [:action/xy])))

(defmethod action-type :mouse-rifle [_]
  (s/and ::action-core
         (s/keys :req-un [:action/xy :action/length :action/direction]
                 :opt-un [::step])))

(defmethod action-type :keyboard [_]
  (s/and ::action-core (s/keys :req-un [:action/keys]
                               :opt-un [:action/delay])))

(defmethod action-type :to-clipboard [_]
  (s/and ::action-core (s/keys :req-un [:action/text])))

(defmethod action-type :open [_]
  (s/and ::action-core (s/keys :req-un [:action/url])))

(defmethod action-type :group [_]
  (s/and ::action-core (s/keys :req-un [:action/actions]
                               :opt-un [:action/repeat-unless])))

(defmethod action-type :scroll [_]
  (s/and ::action-core (s/keys :req-un [:action/diff])))

(s/def ::input (s/or :action :action/action
                     :aciton-seq (s/coll-of :action/action)))

(comment

  (s/valid? :action/action
            {:type    :group
             :name    "Group aciton check"
             :checkpoint true
             :actions [{:type :open
                        :name "Open stargate"
                        :url  "https://stargate.finance/transfer"}

                       {:type  :click
                        :name  "Connect stargate wallet"
                        :xy    [1149 192]
                        :check [{:type :opened!
                                 :regexp  "ya.ru"}
                                {:type      :pattern
                                 :xy        [1324 190]
                                 :length    160
                                 :direction :hor
                                 :await      3000
                                 :regexp    "d{13,}x{17,}(s|x)*x{13,}d{4,}"}]}]}))

(def public-dictionary
  (let [actions  (map str (keys (methods action-type)))
        checks   (map str (keys (methods check-type)))
        keywords (->> (s/registry)
                      keys
                      (map str)
                      (filter #(str/starts-with? % ":action"))
                      (map #(str/replace % #"action/" "")))
        unmapped (map str [:ver :hor :type :mouse1 :mouse2 :mouse3])]
    (into #{} (concat actions checks keywords unmapped))))
