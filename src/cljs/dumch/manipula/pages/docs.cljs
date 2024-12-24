(ns dumch.manipula.pages.docs
  (:require
   [clojure.string :as str]
   [dumch.manipula.app-db :as db]
   [dumch.manipula.components.editor :as editor]
   [reagent.core :as r]
   [dumch.manipula.util :as util]))

;; Define data for docs

(def doc-data
  [{:type :action
    :name ":open"
    :desc "The `:open` action opens a specific URL. The keys `:type`, `:name`, and `:url` are required. `:check []` is optional for any action. `:safe true` means: if `:check` doesn't pass, we continue with the next action. `:wait` is also a common optional field that waits before performing an action."
    :example {:type :open, :name "Open stargate", :url "https://stargate.finance/transfer"}}

   {:type :action
    :name ":group"
    :desc "The `:group` action groups multiple actions together. It requires the keys `:type`, `:name`, and `:actions`. Optionally, we can pass `repeat-unless` object to repeat the whole group. Think of it as \"continue-if\"."
    :example {:type :group, :name "Stargate actions", :wait 200, :actions [{:type :open, :name "Open stargate webpage", :url "https://stargate.finance/transfer"}]
              :repeat-unless {:check {:type :opened! :regexp "ya.ru"}, :times 2}}}

   {:type :action
    :name ":mouse-move"
    :desc "The `:mouse-move` action simulates a mouse move to a specific coordinate (`:xy`). It requires the keys `:type`, `:name`, and `:xy`."
    :example {:type :mouse-move, :name "Move mouse", :xy [1252 118]}}

   {:type :action
    :name ":click"
    :desc "The `:click` action simulates a mouse click at a specific coordinate (`:xy`). It requires the keys `:type`, `:name`, and `:xy`. It moves the mouse automatically to `:xy` position."
    :example {:type :click, :name "Connect stargate wallet", :xy [1252 118]}}

   {:type :action
    :name ":mouse-rifle"
    :desc "The `:mouse-rifle` action simulates multiple mouse clicks, starting from a coordinate (`:xy`), moving in `:direction` with `:step` (5 pixels by default) for `:length` pixels. In the example below, we will click on (`:xy`)s: [0 0], [0 5], [0 10], [0 15], [0 20]."
    :example {:type :mouse-rifle, :name "mouse rifle", :xy [0 0], :step 5, :direction :ver, :length 20}}
   {:type :action
    :name ":keyboard"
    :desc "The `:keyboard` action simulates keyboard input. It requires the keys `:type`, `:name`, and `:keys`. Optionally, it can also have a `:delay` — millis to wait between keys. In the example we use shortcuts to open a new tab, type \"ya.ru\" and press enter, then wait 1000 additional millis, press tab 2 times, and click on x:200,y:300. You can also put a string into clipboard with [\"text to put\"]."
    :example {:type :keyboard, :name "Enter password", :keys [[:cmd :t] "ya.ru" :enter 1000 [:tab 2] [200 300]], :delay 500}}

   {:type :action
    :name ":to-clipboard"
    :desc "The `:to-clipboard` put provided `:text` to system clipboard."
    :example {:name "put" :type :to-clipboard :text "google.com"}}

   {:type :action
    :name "Named action"
    :desc "Any action could be reused by name. In the example, we have an action \"Click MetaMask icon\" which is common and could be reused (even before it's defined)."
    :example [{:name "Click MetaMask icon"}
              {:name "Click MetaMask icon" :type :click :xy [1252 118] :wait 2000}]}

   {:type :action
    :name "Clojure code"
    :desc "You can use basic Clojure code to create an action. Example: create an action with the name 'Name', wait 600ms, type control+t, wait any number of millis between 0 and 1000, type '5.' and random numbers after dot."
    :example '(merge {:name (clojure.string/capitalize (name :key)),
                      :comment "meta: comment"
                      :wait (+ 100 500)}
                     {:type :keyboard, :keys [[:ctrl :t]
                                              (int (rand 1000))
                                              (str (+ 5 (rand 1)))]})}

   {:type :action
    :name ":scroll"
    :desc "The `:scroll` action simulates a mouse scroll. It requires the keys `:type`, `:name`, and `:diff`."
    :example {:type :scroll
              :name "Scroll down"
              :diff -100}}

   ;; checks

   {:type :check
    :name ":pattern"
    :desc "The `:pattern` check checks for a pattern in a defined area. It requires the keys `:type`, `:xy`, `:length`, `:regexp`, `:direction` (:ver or :hor) , and optionally `:await` — millis to wait until pattern matches."
    :example {:name "Some action that was defined before",
              :check {:type :pattern, :xy [1494 555], :regexp "x{10}.{1,3}b{35,45}.{1,3}x{10}", :direction :ver, :length 100, :await 3000}}}

   {:type :check
    :name ":!pattern"
    :desc "The opposite of `:pattern`. In the example, we continue if there is no 30 yellow pixels in a row."
    :example {:name "Some action that was defined before",
              :check {:type :!pattern, :xy [1494 555], :regexp "y{30}", :direction :hor, :length 30}}}

   {:type :check
    :name ":opened!"
    :desc "The `:opened!` check waits for a specific URL to be opened. It requires the keys `:type` and `:regexp`, and optionally `:await`. It has an exlamation mark becuase it performce actions to determine the URL in the browser."
    :example {:name "Some action that was defined before", :check {:type :opened!, :await 4000, :regexp "http.*stargate.finance/transfer"}}}

   {:type :check
    :name ":lang-eng"
    :desc "The `:lang-eng` check verifies that the keyboard layout is English. It requires the keys `:type`"
    :example {:name "Some action that was defined before", :check {:type :lang-eng}}}

   {:type :check
    :name ":focused"
    :desc "The `:focused` check verifies that the app with particular name is currently focused. It requires the keys `:type` and `:regexp`."
    :example {:name "Some action that was defined before", :check {:type :focused, :regexp "Chrome"}}}

   {:type :check
    :name ":screen-size"
    :desc "The `:screen-size` check verifies that the screen size matches the provided dimensions. It requires the keys `:type` and `:xy`."
    :example {:name "Some action that was defined before", :check {:type :screen-size, :xy [1920 1080]}}}

   {:type :check
    :name ":regroup"
    :desc "The `:regroup` is a special `:check` pattern that allows to repeat the parent group. In the provided example, \"cmd+t\" action could trigger the whole \"Group\" to repeat 2 times if `:regroup`->`:check` fails."
    :example {:type :group
              :name "Group"
              :actions [{:type :keyboard, :name "spotlight", :keys [[:cmd :space]]}
                        {:name "cmd+t", :type :keyboard, :keys [[:cmd :t]],
                         :regroup {:check {:type :opened! :regexp "ya.ru"}
                                   :times 2}}]}}

;; common

   {:type :stdlib
    :name "Common actions and keys (stdlib) "
    :desc "A library with the actions and keyboard/keys available in place"
    :example (util/coerce-clj-or-error! (or (db/ws-get :stdlib) "nil"))}])


;; Define state for search field
(def search-field (r/atom ""))

(defn search-filter [data query]
  (println query)
  (let [words   (str/split query " ")
        action? (str/starts-with? "action" (first words))
        check?  (str/starts-with? "check"  (first words))]
    (if (empty? query)
      data
      (filter
       (fn [{:keys [desc] -name :name}]
         (re-find (re-pattern (str "(?i)" (last words))) (str -name " " desc)))
       (cond
         action? (filter #(-> % :type (= :action)) data)
         check?  (filter #(-> % :type (= :check)) data)
         :else   data)))))

(defn action-item [{:keys [name desc example]}]
  [:div.content
   [:h4 name]
   [:p desc]
   [:div
    {:display :flex}
    [editor/editor
     :source (util/coerce-clj-or-error! (str example))]]])

(defn action-list []
  (let [filtered-data (r/track #(search-filter doc-data @search-field))]
    (fn []
      [:div
       (for [item @filtered-data]
       ^{:key item}
         [action-item item])])))

(defn search-input []
  [:div.content
   [:p "Search examples: \"group\", \"act grou\", \"chech open\", \"c open\""]
   [:input.input.is-small.is-rounded.is-primary
    {:type "text"
     :style {:max-width "500px"}
     :autoFocus true
     :placeholder "Search..."
     :value @search-field
     :on-change #(reset! search-field (-> % .-target .-value))}]])

(defn docs-page []
  [:div
   [search-input]
   [action-list]])
