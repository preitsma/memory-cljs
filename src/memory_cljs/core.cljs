(ns memory-cljs.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! chan <!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(def labels
    ["aap","noot","mies","wim","vuur","zus","jet","teun"])


(defn add-card
   "create card element based on a label"
    [v label]
       (conj v {:id (count v) :label label :hidden true :found false}))


(defn create-all-cards
   "create set of cards based on the set of labels"
    [labels]
    (reduce add-card []
         (shuffle
           (concat labels labels))))

(def board
   "atom for the board"
  (atom
    {:cards
      (into []
      (create-all-cards labels))}
    {:turns "0"}))


(defn log-card
   "log one card"
   [card]
   (doseq [[k v] card]
         (.log js/console (str k ": " v ))))

(defn turned-cards
   "return all turned cards"
   [cards]
      (filterv #(false? (:hidden %)) cards))

(defn amount-turned
   "return amount of turned cards"
   [cards]
      (count (turned-cards cards)))


(defn eliminate-equals
   "find two turned cards with equal labels and mark as found"
   [cards]
   (if (= (amount-turned cards) 2)
      (let [turned-cards (turned-cards cards)
            label1       ((first turned-cards) :label)
            label2       ((second turned-cards) :label)]
             (if (= label1 label2)
                 (mapv #(if (= label1 (:label %))
                              (assoc % :found true)
                         %)
                     cards)
              cards))
      cards))


(defn handle-doubles
   "find two turned cards and hide them"
   [cards]
   (let [amount (amount-turned cards)]
      (if (= amount 2)
              (map
                 #(assoc % :hidden true)
                  cards)
       cards)))

(defn toggle
   "handle toggle of a card"
    [cards card]
         (mapv #(if (=  % card)
                   (assoc % :hidden false)
                    % )
              cards))


(defn toggle-card [app card]
  "persist the toggle"
  (when  (:hidden card)
       (om/transact! app :cards
                  #(-> %
                       handle-doubles
                      (toggle card)
                       eliminate-equals))
       (om/transact! app :turns inc)))
  (.log js/console (:turns @board))


(defmulti card-view
   (fn [{found :found} card]
        found))


(defmethod card-view false
  [card owner] (unfound-card-view card owner))

(defmethod card-view true
  [card owner] (found-card-view card owner))


(defn unfound-card-view [card owner]
  (reify
     om/IRenderState
       (render-state [this {:keys [turnaround]}]
          (let [put-card #(put! turnaround @card)
                {label :label
                 hidden :hidden} card]
             (dom/div #js {:className "open-card"
                           :onClick put-card
                           :onTouchEnd put-card}
                     (dom/span #js {:className "label" }
                         (cond (false? hidden) label)))))))

(defn found-card-view [card owner]
  (reify
     om/IRenderState
       (render-state [this {:keys [turnaround]}]
             (dom/div #js {:className "closed-card"}
                     (dom/span #js {:className "label" }
                         "gevonden")))))


(defn memory-board
  "om component for board"
  [app owner]
   (reify
     om/IInitState
     (init-state[_]
       {:turnaround (chan)})
     om/IWillMount
     (will-mount [_]
       (let [turnaround (om/get-state owner :turnaround)]
         (go (loop []
                  (toggle-card app (<! turnaround))
                  (recur))
         )))
     om/IRenderState
     (render-state [this {:keys [turnaround]}]
        (dom/div nil
           (dom/p nil (str "aantal beurten: "
                           (quot (:turns @board) 2)))
           (apply dom/div #js {:className "board"}
               (om/build-all card-view (:cards app)
                 {:init-state {:turnaround turnaround}}))))))

(om/root memory-board board
  {:target (. js/document (getElementById "memory"))})
