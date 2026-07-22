(ns chemeng.store-contract-test
  "MemStore ≡ DatomicStore parity for the Store protocol — proves the
  backend swap (ADR-2607011000 injection boundary) is real: the same
  sequence of operations against either backend produces the same
  observable results."
  (:require [clojure.test :refer [deftest is testing]]
            [chemeng.store :as store]))

(defn- exercise [s]
  (store/register-project! s {:project-id "p1" :name "Test Project" :facility-id "f1"})
  (store/register-facility! s {:facility-id "f1" :name "Test Facility" :location "Lab"})
  (store/commit-record! s {:project-id "p1" :op :draft-process-design :payload "draft" :proposal {:x 1}})
  (store/append-ledger! s {:disposition :commit :record {:project-id "p1"}})
  {:project (store/project s "p1")
   :facility (store/facility s "f1")
   :records (store/records-of s "p1")
   :ledger (store/ledger s)})

(deftest mem-and-datomic-parity
  (testing "same operations against MemStore and DatomicStore observe the same results"
    (let [mem (exercise (store/mem-store))
          dat (exercise (store/datomic-store))]
      (is (= "Test Project" (:name (:project mem))))
      (is (= "Test Project" (:name (:project dat))))
      (is (= "Test Facility" (:name (:facility mem))))
      (is (= "Test Facility" (:name (:facility dat))))
      (is (= 1 (count (:records mem))))
      (is (= 1 (count (:records dat))))
      (is (= :draft-process-design (:op (first (:records mem)))))
      (is (= :draft-process-design (:op (first (:records dat)))))
      (is (= 1 (count (:ledger mem))))
      (is (= 1 (count (:ledger dat))))
      (is (= :commit (:disposition (first (:ledger mem)))))
      (is (= :commit (:disposition (first (:ledger dat))))))))

(deftest datomic-store-nil-lookups-and-empty-filter
  (testing "unregistered project/facility lookups are nil, records-of on an unknown project is empty"
    (let [dat (store/datomic-store)]
      (is (nil? (store/project dat "no-such")))
      (is (nil? (store/facility dat "no-such")))
      (is (empty? (store/records-of dat "no-such"))))))
