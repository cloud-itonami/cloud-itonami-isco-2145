(ns chemeng.advisor
  "ChemicalEngineersAdvisor — proposes a chemical engineering operation
  (draft process design, log process data, flag safety risk, request review)
  for a registered project. Swappable mock/llm; the advisor ONLY proposes —
  `chemeng.governor` checks hard invariants independently. Modeled on
  cloud-itonami-isco-2141's advisor.

  A proposal: {:op :draft-process-design|:log-process-data|:flag-process-safety-risk|:request-client-review
               :effect :propose :project-id str :payload str
               :stake kw :confidence n :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op project-id payload stake] :as request}]
  {:op op
   :effect :propose
   :project-id project-id
   :payload payload
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for project " project-id)})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a chemical engineering advisor. Given a request, propose an :op
   (:draft-process-design, :log-process-data, :flag-process-safety-risk, or
   :request-client-review), the :project-id, a :payload (design draft, data
   log, or risk description), an honest :confidence and a :stake. Safety
   risks MUST be flagged immediately. Never propose issuing a final
   certified engineering design or process-safety certification — that is
   the licensed engineer's role. The governor checks project registration
   and effect scope independently.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
