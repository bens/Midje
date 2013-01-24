(ns ^{:doc "Parsing metadata as found in facts, around-facts, and tables"}
  midje.parsing.1-to-normal-form.metadata
  (:use [midje.error-handling.exceptions :only [user-error]]
        clojure.pprint)
  (:require [midje.parsing.util.arrows :as arrows]))


(def ^{:dynamic true} metadata-for-fact-group {})

(defmacro with-wrapped-metadata [metadata & body]
  `(binding [metadata-for-fact-group (merge metadata-for-fact-group ~metadata)]
         ~@body))

(defn separate-metadata [metadata-containing-form]
  (letfn [(basic-parse [metadata body]
            (let [head (first body)
                  add-key (fn [key value] (assoc metadata key value))]

              (cond (string? head)
                    (recur (add-key :midje/description head) (rest body))

                    (arrows/start-of-checking-arrow-sequence? body)
                    [metadata body] 

                    (symbol? head)
                    (recur (add-key :midje/name (name head)) (rest body))

                    (keyword? head)
                    (recur (add-key head true) (rest body))

                    (map? head)
                    (recur (merge metadata head) (rest body))
                    
                    :else
                    [metadata body])))]
    (let [[metadata body] (basic-parse {:midje/source metadata-containing-form
                                        ;; Storing actual namespaces in these
                                        ;; maps causes bizarre errors in
                                        ;; seemingly unrelated code.
                                        :midje/namespace (ns-name *ns*)
                                        :midje/file *file*
                                        :midje/line (:line (meta metadata-containing-form))}
                                       (rest metadata-containing-form))
          metadata (if (and (contains? metadata :midje/description)
                            (not (contains? metadata :midje/name)))
                     (assoc metadata :midje/name (:midje/description metadata))
                     metadata)]
      [(merge metadata-for-fact-group metadata {:midje/body-source body}) body])))

(defn separate-two-level-metadata [top-form]
  (let [[top-level-meta top-level-body] (separate-metadata top-form)
        lower-level-form (first top-level-body)
        [lower-level-meta lower-level-body] (separate-metadata lower-level-form)
        stripped-top-level-body `((~(first lower-level-form) ~@lower-level-body) ~@(rest top-level-body))]
      [(merge lower-level-meta top-level-meta {:midje/body-source stripped-top-level-body})
       stripped-top-level-body]))
       

(defn separate-multi-fact-metadata
  "This does not include metadata specified by strings or symbols."
  [forms]
  (loop [metadata {}
         [x & xs :as body] forms]
    (cond (keyword? x)
          (recur (assoc metadata x true) xs)
          
          (map? x)
          (recur (merge metadata x) xs)
          
          :else
          [metadata body])))

