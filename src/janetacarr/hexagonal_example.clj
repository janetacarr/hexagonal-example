(ns janetacarr.hexagonal-example
  "This is an incredibly simple example of ports and adapters architecture."
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [sentry-clj.core :as sentry])
  (:gen-class))

;; Driven adapter for Sentry satistfies the "interface"
;; or port that `process-string` expects. In this case the
;; signature for logging-fn is:
;; (Throwable) => nil
;;
;; `capture-exception-adapter` function is adapting
;; the "interface" provided by the sentry-clj lib.
(defn capture-exception-adapter
  [exception]
  (-> exception
      (Throwable->map)
      (sentry/send-event)))

;; Core business logic expects the `logging-fn`
;; interface/port, presumably for side-effects,
;; of the function signature (Throwable) => nil
(defn process-string
  [logging-fn input]
  (try
    (apply str (reverse input))
    (catch Exception e
      (logging-fn e)
      (throw e))))

;; Our business logic Driving adapter. Here it
;; composes our business logic with the driven
;; adapter, but that doesn't necessarily need to be
;; the case. The important thing is to decouple
;; the business logic from the input(s).
(defn process-string-adapter
  [input]
  (process-string capture-exception-adapter input))

;; API handler expects an "interface" or port
;; `biz-logic-fn` that takes a string and
;; returns a string.
(defn ->handler
  [biz-logic-fn]
  (fn handler-fn
    [request]
    (let [input (get-in request [:params :input])]
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (str "{\"result\": \"" (biz-logic-fn input) "\"}")})))

;; Here at the system boundary with either inject or close
;; over our adapters
(let [handler (->handler process-string-adapter)]
  (defroutes app-routes
    (GET "/process/:input" request (handler request))
    (route/not-found "Not Found")))

(def app
  (wrap-defaults app-routes site-defaults))
