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
    ledger     — append-only audit trail, commit or hold."
  )

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
