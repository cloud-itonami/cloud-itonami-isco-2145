(ns chemeng.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [chemeng.actor :as actor]
            [chemeng.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-project! st {:project-id "p1" :name "Test Project" :facility-id "f1"})
    (store/register-facility! st {:facility-id "f1" :name "Test Facility" :location "Lab"})
    st))

(deftest commits-a-valid-design-draft
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:project-id "p1" :op :draft-process-design :payload "Test design draft"}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "p1"))))))

(deftest holds-certification-attempt
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:project-id "p1" :op :issue-certification :payload "Final design"}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "p1")))))

(deftest interrupts-then-accepts-safety-risk-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:project-id "p1" :op :flag-process-safety-risk :payload "Runaway risk"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "p1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "p1")))))))

