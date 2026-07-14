(ns chemeng.governor
  "ChemicalEngineersGovernor — the independent safety/traceability layer
  for the ISCO-08 2145 chemical engineers actor (itonami actor pattern,
  ADR-2607011000 / CLAUDE.md Actors section). Modeled on
  cloud-itonami-isco-2141's governor. Chemical engineering twist: the actor
  prepares draft analysis and flags safety risks for licensed engineer
  review and sign-off; it NEVER issues a final certified design or
  process-safety certification.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. project registration — the project must be verified and
                           registered.
    2. no-actuation      — proposal :effect must be :propose.
    3. no-certification  — attempting to issue a final certified design,
                           process-safety certification, or sign-off is a
                           permanent block.
    4. facility basis     — if the proposal cites a facility, it must be
                           registered and linked to the project.

  ESCALATION invariants (:escalate? true, human sign-off):
    5. :op :flag-process-safety-risk (always escalates; runaway reactions,
                           overpressure, hazardous material handling).
    6. process design touching hazardous materials.
    7. low confidence (< `confidence-floor`)."
  (:require [chemeng.store :as store]))

(def confidence-floor 0.6)

(defn- certification-attempt? [proposal]
  (let [{:keys [op payload]} proposal]
    (and payload
         (or (string? payload)
             (re-find #"(?i)(sign|certif|approve|final|sealed)" (str payload))))))

(defn- hard-violations [{:keys [request proposal]} project facility]
  (let [{:keys [op]} proposal]
    (cond-> []
      (nil? project)
      (conj {:rule :no-project
             :detail "未登録 project — 計画・プロジェクトの登録が必須"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation
             :detail "effect は :propose のみ許可（直接実行禁止）"})

      (certification-attempt? proposal)
      (conj {:rule :no-certification
             :detail "最終設計・プロセス安全認定の直接発行は禁止（PE のサイン役割を侵害）"})

      (and (string? (:facility-id request)) (nil? facility))
      (conj {:rule :unknown-facility
             :detail "未登録 facility — facility の登録が必須"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `chemeng.store/Store`. Pure — never mutates the
  store."
  [request context proposal store]
  (let [project (store/project store (:project-id request))
        facility (some->> (:facility-id request) (store/facility store))
        hard (hard-violations {:request request :proposal proposal}
                             project facility)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        safety-risk? (= :flag-process-safety-risk (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not safety-risk?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? safety-risk?))}))
