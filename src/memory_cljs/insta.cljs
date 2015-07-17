(ns memory-cljs.insta
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [clojure.string :as str]
            [cljs.core.async :refer [put! chan <! >!]]
            [cemerick.url :refer [url-encode]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:import [goog.net Jsonp]
           [goog Uri]))

(def insta-client-id "dea1d49dd5684444a44da24d64c88206")
(def insta-redirect-url "http://localhost:3449/index.html#login")
(def insta-oauth-url "https://instagram.com/oauth/authorize/?client_id=CLIENT-ID&redirect_uri=REDIRECT-URI&response_type=token")
(def insta-user-info-url "https://api.instagram.com/v1/users/self/?access_token=" )
(def insta-most-recent-self-url "https://api.instagram.com/v1/users/self/media/recent/?access_token=")

(defn parse-url [url prop]
  (let [a (.createElement js/document "a")]
    (aset a "href" url)
    (aget a prop)))

(defn get-insta-redirect-url
  "get base url"
  []
  (let [url (.-href (.-location js/window))
        base-url (str (parse-url url "protocol")
                      "//"
                      (parse-url url "host")
                      (parse-url url "pathname"))]
    (.log js/console base-url)
    base-url))

(defn oauth-url
  "creates the oauth url"
  [url client-id redirect-url]
  (str/replace
    (str/replace url #"CLIENT-ID" client-id)
    #"REDIRECT-URI"
    (url-encode redirect-url)))

(defn get-token
  "retrieve the token"
  [url]
  (second (str/split
            (parse-url url "hash") #"token=")))

(defn jsonp [uri]
  (let [out (chan)
        req (Jsonp. (Uri. uri))]
    (.send req nil (fn [res] (put! out res)))
    out))

(defn clear-session
  [app owner]
  (om/set-state! owner :token nil)
  (om/set-state! owner :id nil)
  (om/update! app :token nil)
  (om/update! app :id nil)
  (aset window.location "hash" "#")
  )

(defn login-component
  "renders the login link"
  [app owner]
  (reify
    om/IInitState
    (init-state [_]
      (let [url (.-href (.-location js/window))]
        (om/transact! app :token #(get-token url))
        {:token (get-token url)}
        ))
    om/IWillMount
    (will-mount [_]
      (go
        (let [obj (<! (jsonp (str insta-user-info-url (om/get-state owner :token))))]
          (om/set-state! owner :fullname (-> obj .-data .-full_name))
          (om/set-state! owner :id (-> obj .-data .-id))
          (om/set-state! owner :username (-> obj .-data .-username))
          (om/update! app :id (-> obj .-data .-id))
          ))
      )
    om/IRenderState
    (render-state [_ {:keys [token fullname]}]
      (if token
        (dom/div nil
                 (dom/span nil (str "Logged in as " fullname " "))
                 (dom/button #js {:onClick #(clear-session app owner)}
                             (dom/span nil "logout")))
        (dom/a #js {:href (oauth-url insta-oauth-url insta-client-id (get-insta-redirect-url))}
               (dom/p nil "Login in instagram"))
        ))))