(ns dumch.manipula.components.editor
  (:require
   ["@codemirror/autocomplete" :as autocomplete]
   ["@codemirror/commands" :refer [historyKeymap] :as commands]
   ["@codemirror/language" :refer [defaultHighlightStyle foldCode foldGutter
                                   foldKeymap syntaxHighlighting]]
   ["@codemirror/merge" :refer [MergeView]]
   ["@codemirror/search" :as search]
   ["@codemirror/state" :refer [EditorState StateField]]
   ["@codemirror/view" :as view :refer [EditorView showTooltip]]
   [applied-science.js-interop :as j]
   [clojure.string :as str]
   [dumch.manipula.app-db :as db]
   [dumch.manipula.components.sci-ext :as sci-ext]
   [dumch.manipula.scenario-util :as scenario-util]
   [dumch.manipula.trie :as trie]
   [dumch.manipula.types :as types]
   [dumch.manipula.util :as util]
   [nextjournal.clojure-mode :as cm-clj]
   [nextjournal.clojure-mode.commands :as clj-cmd]
   [nextjournal.clojure-mode.extensions.eval-region :as eval-region]
   [nextjournal.clojure-mode.node :as n]
   [nextjournal.clojure-mode.util :as u]
   [reagent.core :as r]))

;; -------------------------
;; Editor utils

(defn find-text [view-path text]
  (j/let [^:js {{doc :doc} :state} (db/get-in view-path)
          cursor (new search/SearchCursor doc text)
          ^:js {:keys [from to]} (.. cursor next -value)]
    (when-not (= from to 0)
      [from to])))

(defn get-selection [view-path]
  (j/let [^:js {state :state} (db/get-in view-path)
          ^:js {:keys [from to]} (.. state -selection -main)]
    (.sliceDoc state from to)))

#_(get-selection [:dev :editor])

(defn set-selection! [view-path from to]
  (j/let [view (db/get-in view-path)]
    (.focus view)
    (.dispatch view (j/lit {:selection {:anchor from, :head to}
                            :scrollIntoView true}))))

(defn scroll-to-name! [view-path regexp]
  (let [[start end] (find-text view-path regexp)]
    (when start
      (set-selection! view-path start end))))

(defn get-value [view]
  (str (.. view -state -doc)))

(defn get-value-by-path [view-path]
  (j/let [view (db/get-in view-path)]
    (str (.. view -state -doc))))

(j/defn set-value! [^:js {state :state :as view} value]
  (->> {:changes [{:from   0
                   :to     (.. state -doc -length)
                   :insert value}]}
       j/lit
       (.dispatch view)))

(defn set-value-by-path! [view-path value]
  (set-value! (db/get-in view-path) value))

(defn get-cursor [view]
  (j/let [^js [{:keys [from]}]  (.. view -state -selection -ranges)]
    from))

(j/defn align-editor [^:js {:keys [state] :as view}]
  (let [aligned (-> (.-doc state)
                    str
                    util/coerce-clj-or-error!)
        cursor (get-cursor view)]
    (->> {:changes {:from   0
                    :to     (.. state -doc -length)
                    :insert aligned}
          :selecton {:anchor cursor
                     :head cursor}}
         j/lit
         (.dispatch view))))

(defn align-editor-by-path [path]
  (align-editor (db/get-in path)))

;; -------------------------
;; Completion

(defn- generate-map-autocomplete [-type kv]
  {:label (str "type: " -type)
   :text (str
          "{:type " -type "\n "
          (str/join "\n " (for [[k v] kv]
                            (str k " " v)))
          "}")})

(def patterns-dictionary
  [(generate-map-autocomplete :keyboard {:name "\"\"" :keys []})
   (generate-map-autocomplete :pattern {:xy ['x 'y] :regexp "\"\"" :direction \_ :length \_ :await 10000})
   (generate-map-autocomplete :group {:name "\"\"" :actions []})])

(j/defn apply-input-method-snippet
  [^js view ^:js {insert :detail} from to]
  (.dispatch view (j/lit {:changes     [{:from   (dec from)
                                         :to     to
                                         :insert insert}]
                          :selection   {:anchor (+ (dec from) (count insert))}
                          :annotations (u/user-event-annotation "noformat")})))

(j/defn apply-input-method [^js view ^:js {insert :detail} from to]
  (.dispatch view (j/lit {:changes     [{:from   from
                                         :to     to
                                         :insert insert}]
                          :selection   {:anchor (+ from (count insert))}
                          :annotations (u/user-event-annotation "noformat")})))


(defn- token-before [^js state pos]
  (j/let [^:js {line-from :from :keys [text]} (.. state -doc (lineAt pos))]
    (if-let [text (re-find #"\\[^ ]*$" (subs text 0 (- pos line-from)))]
      {:from (- pos (count text))
       :to   pos
       :text text}
      (j/let [^:js {:keys [from to]} (n/tree state pos -1)]
        {:from from
         :to   to
         :text (subs text (- from line-from) (- to line-from))}))))

(def snippet-completer
  (j/fn [^:js {:keys [state pos]}]
    (let [{:keys [text from]} (token-before state pos)]
      (when (str/starts-with? text "\\") ;}
            #js{:from    (inc from)
                :options (to-array
                          (for [{:keys [label text]} patterns-dictionary]
                            (j/obj :label label
                                   :input-method true
                                   :apply apply-input-method-snippet
                                   :detail text)))}))))

(def clojure-core-trie
  (->> "(ns-publics 'clojure.core)"
       sci-ext/eval-string
       keys
       (mapv str)
       trie/trie))

(def clojure-core-completer
  (j/fn [^:js {:keys [state pos]}]
    (let [{:keys [text from]}
          (token-before state pos)
          
          keywords
          (clojure-core-trie text)]

      (when (seq keywords)
        #js{:from    from
            :options (to-array
                      (for [label keywords]
                        (j/obj :label (str "clojure:" label)
                               :input-method true
                               :apply apply-input-method
                               :detail label)))}))))

(def custom-define-completer
  (j/fn [^:js {:keys [state pos]}]
    (let [{:keys [text from]}
          (token-before state pos)

          keywords
          (->> "(ns-publics *ns*)"
               sci-ext/eval-string
               keys
               (map str)
               (filter #(str/includes? % text)))]
      (when (seq keywords)
        #js{:from    from
            :options (to-array
                      (for [label keywords]
                        (j/obj :label (str "local:" label)
                               :input-method true
                               :apply apply-input-method
                               :detail label)))}))))

(def static-keys-completer
  (autocomplete/completeFromList
     (clj->js types/public-dictionary)))

(def completion-ext
  (autocomplete/autocompletion
   (j/lit {:activateOnTyping true,
           :override [static-keys-completer
                      custom-define-completer
                      clojure-core-completer
                      snippet-completer]})))

;; -------------------------
;; Extensions

(def theme
  (.theme EditorView
          (j/lit {".cm-content" {:white-space "pre-wrap"
                                 :padding "10px 0"
                                 :flex "1 1 0"}
                  ".cm-scroller" {:overflow "auto"}

                  ".cm-tooltip.cm-tooltip-cursor"
                  {:backgroudColor "#66b"
                   :border "1px solid var(--teal-color)"
                   :padding "2px 7px"
                   :borderRadius "4px"}

                  "&.cm-focused" {:outline "0 !important"}
                  ".cm-line" {:padding "0 9px"
                              :line-height "1.6"
                              :font-size "16px"
                              :font-family "var(--code-font)"}
                  ".cm-matchingBracket" {:border-bottom "1px solid var(--teal-color)"
                                         :color "inherit"}
                  ".cm-gutters" {:background "transparent"
                                 :border "none"}
                  ".cm-gutterElement" {:margin-left "5px"}
                  ;; ".cm-cursor" {:visibility "hidden"}
                  "&.cm-focused .cm-cursor" {:visibility "visible"}})))

(defn restrict-heigh [max-heigh]
  (.theme EditorView
          (j/lit {"&" {:max-height (str max-heigh "px")}})))

(def ^:private custom-keys-extension
  (.of view/keymap
       (j/lit
        [{:key "Ctrl-l" :run align-editor}
         {:key "Ctrl-e" :run foldCode}
         {:key "Alt-k"  :run (clj-cmd/view-command (clj-cmd/slurp 1))}
         {:key "Alt-b"  :run (clj-cmd/view-command (clj-cmd/barf 1))}
         {:key "Alt-Shift-k" :run (clj-cmd/view-command (clj-cmd/slurp -1))}
         {:key "Alt-Shift-b" :run (clj-cmd/view-command (clj-cmd/barf -1))}])))

(defonce extensions
  #js [theme
       (commands/history)
       completion-ext
       (syntaxHighlighting defaultHighlightStyle)
       (.-lineWrapping EditorView) ;; without this won't scroll with mouse wheel
       (foldGutter)
       (.. EditorState -allowMultipleSelections (of true))
       cm-clj/default-extensions
       (.of view/keymap foldKeymap)
       custom-keys-extension
       (.of view/keymap cm-clj/complete-keymap)
       (.of view/keymap historyKeymap)

       (view/lineNumbers)])

;; -------------------------
;; Dynamic extension

(defn- ->on-cahnge [f]
  (.. EditorView
      -updateListener
      (of (fn [_]
            (f nil))))
  #_(.. EditorView -inputHandler (of (fn [_ _ _ text] (f text) false))))

(defn- get-cursor-tooltips [eval-path state]
  (if (db/get-in eval-path)
    (j/lit
     [{:pos (.. state -selection -main -head)
       :above true
       :strictSide true
       :arrow false
       :create (fn []
                 (let [dom (js/document.createElement "div")]
                   (set! (.-className dom) "cm-tooltip-cursor")
                   (set! (.-textContent dom) (db/get-in! eval-path))
                   #js {:dom dom}))}])
    #js []))

(defn- cursor-tooltip-field [eval-path]
  (.define
   StateField
   (j/obj :create (partial get-cursor-tooltips eval-path)
          :update (j/fn [tooltips ^:js {:keys [docChanged selection state]}]
                    (if (or docChanged selection (db/get-in eval-path))
                      (get-cursor-tooltips eval-path state)
                      tooltips))
          :provide (fn [f]
                     (.computeN showTooltip #js [f] (fn [state] (.field state f)))))))

(j/defn on-selection [on-result ^:js {:keys [state]}]
  (j/let [^:js {:keys [from to]} (.. state -selection -main)
          selection (.sliceDoc state from to)]
    (on-result selection)
    true))

(defn- do-with-selection-extension [on-result]
  (.of view/keymap
       (j/lit
        [{:key "Ctrl-s"
          :run (partial on-selection on-result)}])))

(defn editor
  [& {:keys [editor-path source eval-result-path
             on-destroy on-change on-selection]}]
  (println "editor recreated?" on-selection)
  (r/with-let
    [editor-path (or editor-path [:rand-editor (scenario-util/rand-str 9)])
     mount! (fn [el]
              (when el
                (db/assoc-in!
                 editor-path
                 (new EditorView
                      (j/obj
                       :extensions
                       (cond-> #js [(.concat (restrict-heigh 700) extensions)]

                         on-change
                         (.concat #js [(->on-cahnge on-change)])

                         on-selection
                         (.concat #js [(do-with-selection-extension
                                        on-selection)])

                         eval-result-path
                         (.concat
                          #js [(cursor-tooltip-field eval-result-path)
                               (eval-region/extension {:modifier "Alt"})
                               (sci-ext/extension
                                {:modifier "Alt"
                                 :on-result #(db/assoc-in! eval-result-path %)})]))
                       :doc source
                       :parent el)))))]
    [:div.rounded-md.mb-0.text-sm.monospace.overflow-auto.relative.border.shadow-lg.bg-white
     {:ref mount!}]
    (finally
      (j/call (db/get-in! editor-path) :destroy)
      (db/assoc-in! editor-path nil)
      (when on-destroy (on-destroy)))))

(defn merge-view [editor-path content-a content-b]
  (r/with-let [mount!
               (fn [el]
                 (when el
                   (db/assoc-in!
                    editor-path
                    (new MergeView
                         (j/lit
                          {:a  {:extensions #js [extensions]
                                :parent el
                                :doc content-a}

                           :b {:extensions #js [extensions]
                               :parent el
                               :doc content-b}
                           :parent el})))))]
    [:div.rounded-md.mb-0.text-sm.monospace.overflow-auto.relative.border.shadow-lg.bg-white
     {:ref mount!}]
    (finally
      (j/call (db/get-in! editor-path) :destroy)
      (db/assoc-in! editor-path nil))))
