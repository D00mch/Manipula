(ns dumch.manipula.web.controllers.play
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [robot.core :as r])
  (:import
   (com.microsoft.playwright Playwright)
   (com.microsoft.playwright Locator$WaitForOptions)))

(def ^:cons ads-url "http://local.adspower.net:50325/api/v1/browser")

(defn curl [url]
  (-> (slurp url)
      (json/read-str :key-fn keyword)))

(defn open-ads [id-profile]
  (curl
   (format "%s/start?headless=%d&serial_number=%s&launch_args=%s"
           ads-url
           0
           id-profile
           "[]")))

(defn check-opened [id-profile]
  (curl
   (format "%s/active?headless=%d&serial_number=%s&launch_args=%s"
           ads-url
           0
           id-profile
           "[]")))

(defn close-ads  [id-profile]
  (curl (format "%s/stop?serial_number=%s" ads-url id-profile)))

(def pw (Playwright/create))
(def chm (.chromium pw))

(defn new-page! [browser]
  (.newPage (.. browser contexts (get 0))))

(defn hinder-page [browser]
  (.. browser contexts (get 0) pages (get 0)))

(defn wait-for [locator millis]
  (.waitFor
   locator
   (doto (Locator$WaitForOptions.)
     (.setTimeout millis))))

(defn ensure-opened!
  "Getting ADS `id-profile`,
   make sure browser is opened and return playwright.browser instance"
  [id-profile]
  (let [{{status :status} :data :as resp}
        (check-opened id-profile)

        {msg :msg, {port :debug_port :as data} :data :as resp}
        (if (= status "Inactive")
          (open-ads id-profile)
          resp)

        _
        (when-not (= (str/lower-case msg) "success")
          (throw (ex-info msg {:data data})))

        browser
        (.connectOverCDP chm (str "http://127.0.0.1:" port))]

    browser))

#_(close-ads 172)
#_(ensure-opened! 172)

(def ^:const url-metamask "chrome-extension://kmamddnkddhpblbhpmdpljfkceaddeel")
(def ^:const url-argentx  "chrome-extension://lbmkfffmegekbbjacedjkjfindkaabjc")
(def ^:const url-braavos "chrome-extension://meejlkofnnmnccfklihpgakicgiopncj")

(def ^:dynamic *browser* nil)
(def ^:dynamic *page* nil)

(defmacro with-browser [id-profile & body]
  `(let [br# (ensure-opened! ~id-profile)]
     (with-bindings {#'*browser* br#
                     #'*page* (new-page! br#)}
       (try
         ~@body
         (catch Throwable e#
           (.close *page*))
         (finally
           (.close *browser*))))))

#_(def browser-id (ensure-opened! 219))
#_(def page (new-page! browser-id))
#_(def hinder page)

(defn login-mm-seed
  ([id-profile seed password url-metamask]
   (with-browser id-profile
     (login-mm-seed seed password *page* (hinder-page *browser*) url-metamask)))
  ([seed password page hinder url-metamask]
   (let [lang-locator (.locator page "//*[@id=\"app-content\"]/div/div[1]/div/div/select")]

     (.navigate page (str url-metamask "/home.html#onboarding/metametrics"))
     (wait-for lang-locator 120000)
     (.selectOption lang-locator "English")

     (.. page (getByText "I agree") click)

     (.. page (waitForTimeout 2000))
     (when-let [locator (.. page (querySelector "text=I agree to MetaMask"))]
       (.. locator click)
       (.. page (waitForSelector "button[data-testid='onboarding-import-wallet']:not([disabled])") click)
       (.. page (getByText "I agree") click))

      ;; enter seed
     (let [path-format "//*[@id=\"import-srp__srp-word-%d\"]"]
       (.. page (locator (format path-format 0)) click)
       (.. hinder bringToFront)
       (r/mouse-move! 0 0)
       (doseq [[i word] (map-indexed vector (str/split seed #" "))]
         (.. hinder bringToFront)
         (.. page
             (locator (format path-format i))
             (fill word)))
       (.. page (getByText "Confirm Secret Recovery Phrase") (nth 1) click))

      ;; enter password
     (let [xpath "//*[@id=\"app-content\"]/div/div[2]/div/div/div/div[2]/form/div[1]/label/input"
           xpath2 "//*[@id=\"app-content\"]/div/div[2]/div/div/div/div[2]/form/div[2]/label/input"]
       (.. hinder bringToFront)
       (.. page (locator xpath) (fill password))
       (.. hinder bringToFront)
       (.. page (locator xpath2) (fill password))
       (.. hinder bringToFront)
       (.. page (getByText "I understand that") click)
       (.. hinder bringToFront)
       (.. page (waitForSelector "button[data-testid='create-password-import']:not([disabled])") click))

      ;; click throug security warnings
     (.. page (waitForTimeout 2000))
     (when-let [locator (.. page (querySelector "text=Remind me later"))]
       (.. locator click)
       (.. page (getByText "I understand that") click)
       (.. page (locator "//*[@id=\"popover-content\"]/div/div/section/div[2]/div/button[2]") click))

     (.. page (getByText "Got it!") click)
     (.. page (getByText "Next") click)
     (.. page (locator "//*[@id=\"app-content\"]/div/div[2]/div/div/div/div[2]/button") click)
     (.. page bringToFront))))

(defn login-mm-password
  ([id-profile password url]
   (with-browser id-profile
     (login-mm-password password *page* (hinder-page *browser*) url)))
  ([password page hinder url]
   (let [pass-locator (.locator page "//*[@id=\"password\"]")]
     (.navigate page (str url "/home.html#onboarding/metametrics"))
     (.. hinder bringToFront)
     (wait-for pass-locator 120000)
     (.. hinder bringToFront)
     (.. pass-locator (fill password))
     (.. hinder bringToFront)
     (.. page (locator "//*[@id=\"app-content\"]/div/div[2]/div/div/button") click)
     (.. page bringToFront))))

(defn login-braavos-seed
  ([id-profile seed password url]
   (with-browser id-profile
     (login-braavos-seed seed password *page* (hinder-page *browser*) url)))
  ([seed password page hinder-page url]
   (let [import-locator (.getByText page "Import Your Braavos Wallet")]
     (.navigate page (str url "/index.html"))
     (.. hinder-page bringToFront)
     (wait-for import-locator 1200)
     (.. import-locator click)
     (r/mouse-move! 0 0)
     (.. hinder-page bringToFront)
     (r/mouse-move! 0 0)
     (.. page
         (locator "//*[@id=\"root\"]/div/div/div/div/div/div/div/div/div/div[2]/div[1]/div[3]/div/div/div[2]/textarea")
         (fill seed))
     (r/mouse-move! 0 0)
     (.. hinder-page bringToFront)
     (.. page
         (locator "//*[@id=\"root\"]/div/div/div/div/div/div/div/div/div/div[2]/div[2]/div[2]")
         click)
     (.. page
         (locator "//*[@id=\"root\"]/div/div/div/div/div/div/div/div/div/div[1]/div[2]/div/div/div[2]/div[1]/div/div[2]/input")
         (fill password))
     (.. page
         (locator "//*[@id=\"root\"]/div/div/div/div/div/div/div/div/div/div[1]/div[2]/div/div/div[3]/div[1]/div/div[2]/input")
         (fill password))
     (.. page
         (locator "//*[@id=\"root\"]/div/div/div/div/div/div/div/div/div/div[1]/div[2]/div/div/div[4]/div/div[2]")
         click)
     (.. page bringToFront))))

(defn login-braavos-password
  ([id-profile password url]
   (with-browser id-profile
     (login-braavos-password password *page* (hinder-page *browser*) url)))
  ([password page hinder url]
   (let [pass-locator
         (.locator
          page
          "//*[@id=\"root\"]/div/div/div/div/div/div/div/div/div/div[1]/div[3]/div/div[2]/div/div/div[2]/input")]
     (.navigate page (str url "/index.html"))
     (r/mouse-move! 0 0)
     (wait-for pass-locator 120000)
     (.. hinder bringToFront)
     (.. pass-locator (fill password))
     (.. page
         (locator "//*[@id=\"root\"]/div/div/div/div/div/div/div/div/div/div[1]/div[3]/div/div[3]/div/div[2]")
         click)
     (.. page bringToFront))))

(defn login-argentx-seed
  ([id-profile seed password url]
   (with-browser id-profile
     (login-argentx-seed seed password *page* (hinder-page *browser*) url)))
  ([seed password page hinder url]
   (let [restore-locator (.locator page "//*[@id=\"root\"]/div/div/div/div/div[1]/div/div[3]/button[2]")]
     (.navigate page (str url "/index.html"))
     (wait-for restore-locator 120000)
     (.. restore-locator click)

      ;; seed auth
     (let [path-format "//*[@id=\"root\"]/div/div/div/div/div[1]/div/form/div[1]/div[%d]/input"]
       (.. hinder bringToFront)
       (r/mouse-move! 0 0)
       (doseq [[i word] (map-indexed vector (str/split seed #" "))]
         (.. hinder bringToFront)
         (.. page
             (locator (format path-format (+ 2 i)))
             (fill word)))
       (.. page (getByText "Continue") click))

      ;; password-auth
     (let [xpath "xpath=/html/body/div[1]/div/div/div/div/div[1]/div/form/input[1]"
           xpath2 "xpath=/html/body/div[1]/div/div/div/div/div[1]/div/form/input[2]"]
       (.. hinder bringToFront)
       (.. page (locator xpath) (fill password))
       (.. hinder bringToFront)
       (.. page (locator xpath2) (fill password))
       (.. hinder bringToFront)
       (.. page (waitForSelector "button.chakra-button.css-1535foh:not([disabled])") click))

     (.. page (getByText "Finish") click))))

(defn login-argentx-password
  ([id-profile password url]
   (with-browser id-profile
     (login-argentx-password password *page* (hinder-page *browser*) url)))
  ([password page hinder url]
   (let [pass-locator (.locator page "xpath=/html/body/div[1]/div/div/div/div/div/div/div/div[2]/form/input")]
     (.navigate page (str url "/index.html"))
     (wait-for pass-locator 120000)
     (.. hinder bringToFront)
     (.. pass-locator (fill password))
     (.. hinder bringToFront)
     (.. page (locator "xpath=/html/body/div[1]/div/div/div/div/div/div/div/div[2]/form/div/button") click)
     (.. page bringToFront))))

(comment
  (def seed "powder amazing door acoustic genre copper eternal speak glide satisfy element balance")
  (def seed "flower rent rule chimney hamster open decline sword army oak dinner ritual")
  (def password "M8haScw+k1XL#cH")
  (def id-profile 219)
  (login-argentx-seed id-profile seed password url-argentx)
  (login-argentx-password id-profile password url-argentx)
  (login-mm-seed id-profile seed password url-metamask)
  (login-mm-password id-profile password url-metamask)
  (login-braavos-seed id-profile seed password url-braavos)
  (login-braavos-password id-profile password url-braavos))
