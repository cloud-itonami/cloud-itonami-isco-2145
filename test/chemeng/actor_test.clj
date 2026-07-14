(ns chemeng.actor-test
  (:require [clojure.test :refer [deftest testing is]]
            [chemeng.actor :as actor]
            [chemeng.store :as store]
            [chemeng.advisor :as advisor]))

(deftest actor-graph-intake-to-advise
  (testing "graph flows from intake through advise"
    (let [s (store/mem-store)
          graph (actor/build-graph {:store s})
          req {:project-id "p1" :op :draft-process-design :payload "test"}
          result (actor/run-request! graph req nil "thread-1")]
      (is (map? result))
      (is (:proposal result)))))

(deftest actor-graph-hard-hold
  (testing "hard violation -> :hold disposition"
    (let [s (store/mem-store)
          graph (actor/build-graph {:store s})
          req {:project-id "unknown" :op :draft-process-design}
          result (actor/run-request! graph req nil "thread-2")]
      (is (map? result))
      (is (nil? (:record result)))
      (is (some #(= :hold %) (map :node (:audit result)))))))

(deftest actor-graph-escalation-interrupt
  (testing "escalation triggers request-approval interrupt"
    (let [s (store/mem-store {:projects {"p1" {:project-id "p1" :name "Test Project"}}})
          graph (actor/build-graph {:store s})
          req {:project-id "p1" :op :flag-process-safety-risk :payload "Test risk"}
          result (actor/run-request! graph req nil "thread-3")]
      (is (map? result))
      (is (some #(= :request-approval %) (map :node (:audit result)))))))

(deftest actor-graph-commit
  (testing "valid proposal -> commit"
    (let [s (store/mem-store {:projects {"p1" {:project-id "p1" :name "Test Project"}}})
          graph (actor/build-graph {:store s})
          req {:project-id "p1" :op :draft-process-design :payload "valid design"}
          result (actor/run-request! graph req nil "thread-4")]
      (is (map? result))
      (is (:record result))
      (is (some #(= :commit %) (map :node (:audit result)))))))

(deftest actor-graph-approval-resume
  (testing "approve! resumes interrupted flow"
    (let [s (store/mem-store {:projects {"p1" {:project-id "p1" :name "Test Project"}}})
          graph (actor/build-graph {:store s})
          req {:project-id "p1" :op :flag-process-safety-risk :payload "Risk to approve"}
          result-1 (actor/run-request! graph req nil "thread-5")
          result-2 (actor/approve! graph "thread-5")]
      (is (map? result-2))
      (is (:record result-2))
      (is (some #(= :commit %) (map :node (:audit result-2)))))))

(deftest actor-audit-ledger
  (testing "all operations append to ledger"
    (let [s (store/mem-store {:projects {"p1" {:project-id "p1" :name "Test Project"}}})
          graph (actor/build-graph {:store s})
          req {:project-id "p1" :op :draft-process-design :payload "test"}
          _ (actor/run-request! graph req nil "thread-6")
          ledger (store/ledger s)]
      (is (not-empty ledger))
      (is (some #(= :commit (:disposition %)) ledger)))))
