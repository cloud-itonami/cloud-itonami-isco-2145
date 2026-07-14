(ns chemeng.governor-test
  (:require [clojure.test :refer [deftest testing is]]
            [chemeng.governor :as governor]
            [chemeng.store :as store]))

(deftest hard-violations-project-registration
  (testing "no project -> violation"
    (let [s (store/mem-store)
          req {:project-id "unknown-proj"}
          prop {:op :draft-process-design :effect :propose :payload "test"}
          verdict (governor/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :no-project (:rule %)) (:violations verdict))))))

(deftest hard-violations-effect-gating
  (testing "non-:propose effect -> violation"
    (let [s (store/mem-store {:projects {"p1" {:project-id "p1" :name "Test Project"}}})
          req {:project-id "p1"}
          prop {:op :draft-process-design :effect :commit :payload "test"}
          verdict (governor/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :no-actuation (:rule %)) (:violations verdict))))))

(deftest hard-violations-certification-attempt
  (testing "certification attempt -> violation"
    (let [s (store/mem-store {:projects {"p1" {:project-id "p1" :name "Test Project"}}})
          req {:project-id "p1"}
          prop {:op :draft-process-design :effect :propose :payload "Final Certified Design"}
          verdict (governor/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :no-certification (:rule %)) (:violations verdict))))))

(deftest escalation-safety-risk
  (testing ":flag-process-safety-risk always escalates"
    (let [s (store/mem-store {:projects {"p1" {:project-id "p1" :name "Test Project"}}})
          req {:project-id "p1"}
          prop {:op :flag-process-safety-risk :effect :propose :payload "Runaway reaction risk"
                :confidence 0.95}
          verdict (governor/check req nil prop s)]
      (is (:escalate? verdict))
      (is (not (:hard? verdict))))))

(deftest escalation-low-confidence
  (testing "low confidence -> escalation"
    (let [s (store/mem-store {:projects {"p1" {:project-id "p1" :name "Test Project"}}})
          req {:project-id "p1"}
          prop {:op :draft-process-design :effect :propose :payload "test" :confidence 0.5}
          verdict (governor/check req nil prop s)]
      (is (:escalate? verdict))
      (is (not (:hard? verdict))))))

(deftest ok-verdict
  (testing "valid proposal -> :ok?"
    (let [s (store/mem-store {:projects {"p1" {:project-id "p1" :name "Test Project"}}})
          req {:project-id "p1"}
          prop {:op :draft-process-design :effect :propose :payload "test process design"
                :confidence 0.85}
          verdict (governor/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:hard? verdict)))
      (is (not (:escalate? verdict))))))

(deftest facility-registration
  (testing "facility required -> violation if missing"
    (let [s (store/mem-store {:projects {"p1" {:project-id "p1" :name "Test Project"}}})
          req {:project-id "p1" :facility-id "unknown-fac"}
          prop {:op :log-process-data :effect :propose :payload "test data" :confidence 0.85}
          verdict (governor/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :unknown-facility (:rule %)) (:violations verdict))))))
