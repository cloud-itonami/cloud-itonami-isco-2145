(ns chemeng.store
  "SSoT for the ISCO-08 2145 chemical engineers actor (itonami actor
  pattern, ADR-2607011000 / CLAUDE.md Actors section). Modeled on
  cloud-itonami-isco-2141's manufacturing QA store, adapted for
  chemical process design scope.

  Domain:

    project    — a registered project (:project-id, :name, :facility-id)
    facility   — a registered facility (:facility-id, :name, :location)
    record     — a committed operating record (process design draft,
                 safety data log, or risk flag) — written ONLY via
                 commit-record!.
    ledger     — append-only audit trail, commit or hold.

  Two backends implement the same `Store` protocol so the backend is a
  swap, not a rewrite (the injection boundary, ADR-2607011000):

    - `MemStore`     — atom of EDN. The deterministic default for
                       dev/tests/demo (no deps).
    - `DatomicStore` — backed by `langchain.db`, a Datomic-API-compatible
                       EAV store (swappable to a kotoba-server pod in
                       production). project/facility/record/ledger
                       entries carry free-form fields, so each is stored
                       as an EDN-blob payload via `langchain-store.core`
                       (`ls/enc`/`ls/dec*`), not a hand-rolled codec
                       (ADR-2607141600).

  Both pass the same contract (test/chemeng/store_contract_test.clj)."
  (:require [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (project [s project-id])
  (facility [s facility-id])
  (records-of [s project-id])
  (ledger [s])
  (register-project! [s proj])
  (register-facility! [s fac])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (project [_ project-id] (get-in @a [:projects project-id]))
  (facility [_ facility-id] (get-in @a [:facilities facility-id]))
  (records-of [_ project-id] (filter #(= project-id (:project-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-project! [s proj]
    (swap! a assoc-in [:projects (:project-id proj)] proj) s)
  (register-facility! [s fac]
    (swap! a assoc-in [:facilities (:facility-id fac)] fac) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:projects {} :facilities {} :records [] :ledger []}
                                   seed)))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  (ls/identity-schema [:project/id :facility/id :record/seq :ledger/seq]))

(defn- blob-lookup
  "Look up the EDN-blob payload for the entity uniquely identified by
  `id-attr`/`id` and stored under `payload-attr`."
  [conn id-attr payload-attr id]
  (ls/dec* (d/q {:find '[?p .] :in '[$ ?id]
                 :where [['?e id-attr '?id] ['?e payload-attr '?p]]}
               (d/db conn) id)))

(defrecord DatomicStore [conn]
  Store
  (project [_ project-id] (blob-lookup conn :project/id :project/payload project-id))
  (facility [_ facility-id] (blob-lookup conn :facility/id :facility/payload facility-id))
  (records-of [_ project-id] (filter #(= project-id (:project-id %)) (ls/read-stream conn :record/seq :record/payload)))
  (ledger [_] (ls/read-stream conn :ledger/seq :ledger/payload))
  (register-project! [s proj]
    (d/transact! conn [{:project/id (:project-id proj) :project/payload (ls/enc proj)}]) s)
  (register-facility! [s fac]
    (d/transact! conn [{:facility/id (:facility-id fac) :facility/payload (ls/enc fac)}]) s)
  (commit-record! [s record]
    (ls/append-blob! conn :record/seq :record/payload (count (ls/read-stream conn :record/seq :record/payload)) record) s)
  (append-ledger! [s fact]
    (ls/append-blob! conn :ledger/seq :ledger/payload (count (ls/read-stream conn :ledger/seq :ledger/payload)) fact) s))

(defn datomic-store
  ([] (datomic-store {}))
  ([seed]
   (let [s (->DatomicStore (d/create-conn schema))]
     (doseq [[id proj] (:projects seed)] (register-project! s (assoc proj :project-id id)))
     (doseq [[id fac] (:facilities seed)] (register-facility! s (assoc fac :facility-id id)))
     (doseq [record (:records seed)] (commit-record! s record))
     (doseq [fact (:ledger seed)] (append-ledger! s fact))
     s)))
