(ns dumch.manipula.components.sci-ext
  (:require ["@codemirror/view" :as view]
            [applied-science.js-interop :as j]
            [clojure.string :as str]
            [nextjournal.clojure-mode.extensions.eval-region :as eval-region]
            [sci.core :as sci]))

(defonce !sci-ctx
  (atom (sci/init {:async? true
                   :disable-arity-checks true
                   :classes {'js goog/global
                             :allow :all}})))

(defn eval-string
  ([source]
   (when-some [code (not-empty (str/trim source))]
     (try (sci/eval-string code @!sci-ctx)
          (catch js/Error e
            {:error (str (.-message e))})))))

(j/defn eval-at-cursor [on-result ^:js {:keys [state]}]
  (some->> (eval-region/cursor-node-string state)
           (eval-string)
           (on-result))
  true)

(j/defn eval-top-level [on-result ^:js {:keys [state]}]
  (some->> (eval-region/top-level-string state)
           (eval-string)
           (on-result))
  true)

(j/defn eval-cell [on-result ^:js {:keys [state]}]
  (-> (.-doc state)
      (str)
      (eval-string)
      (on-result))
  true)

(defn extension [{:keys [modifier
                         on-result]}]
  (.of view/keymap
       (j/lit
        [{:key "Mod-Enter"
          :run (partial eval-cell on-result)}
         {:key (str modifier "-Enter")
          :shift (partial eval-top-level on-result)
          :run (partial eval-at-cursor on-result)}])))
