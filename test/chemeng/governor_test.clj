(ns chemeng.governor-test
  (:require [clojure.test :refer [deftest testing is]]
            [chemeng.governor :as governor]
            [chemeng.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-project! st {:project-id "p1" :name "Test Project" :facility-id "f1"})
    (store/register-facility! st {:facility-id "f1" :name "Test Facility" :location "Lab"})
    st))

(defn- draft-design [payload confidence]
  {:op :draft-process-design :effect :propose :payload payload :confidence confidence})

(def ^:private req {:project-id "p1"})

(deftest ok-valid-design
  (let [st (fresh-store)
        v (governor/check req {} (draft-design "Process design draft" 0.85) st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest hard-on-no-project
  (let [st (store/mem-store)
        v (governor/check {:project-id "unknown"} {} (draft-design "test" 0.85) st)]
    (is (:hard? v))
    (is (some #(= :no-project (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (draft-design "test" 0.85) :effect :commit) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-certification-attempt
  (let [st (fresh-store)
        v (governor/check req {} (assoc (draft-design "test" 0.85) :op :issue-certification) st)]
    (is (:hard? v))
    (is (some #(= :no-certification (:rule %)) (:violations v)))))

(deftest escalates-safety-risk
  (let [st (fresh-store)
        v (governor/check req {} {:op :flag-process-safety-risk :effect :propose
                                  :payload "Runaway risk" :confidence 0.9} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (draft-design "test" 0.5) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest hard-on-unknown-facility
  (let [st (fresh-store)
        v (governor/check (assoc req :facility-id "unknown-fac") {} (draft-design "test" 0.85) st)]
    (is (:hard? v))
    (is (some #(= :unknown-facility (:rule %)) (:violations v)))))
