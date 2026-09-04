import { reactive } from 'vue'

const clone = (value) => value == null ? value : structuredClone(value)

const draftFields = [
  'startDate', 'endDate', 'budgetAmount', 'budgetCurrency', 'partySize',
  'preferences', 'destinations',
]

const requestDraft = (source) => Object.fromEntries(
  draftFields.map((key) => [key, clone(source?.[key])]),
)

const errorState = (error) => (
  ['PROPOSAL_EXPIRED', 'VERSION_CONFLICT'].includes(error?.errorCode) ? 'expired' : 'failed'
)

export function createItineraryPlanning({ itineraryId, api, itineraryApi, uuid = () => crypto.randomUUID() }) {
  const id = String(itineraryId)
  let saving = null
  let generating = null
  let confirming = null
  let rejecting = null
  let confirmAttempt = null
  let rejectAttempt = null

  const state = reactive({
    status: 'idle',
    request: null,
    proposals: [],
    proposal: null,
    selectedOperationKeys: new Set(),
    itinerarySnapshot: null,
    error: null,
  })

  const replaceSelection = (keys) => {
    state.selectedOperationKeys.clear()
    for (const key of keys) state.selectedOperationKeys.add(key)
  }

  const setProposal = (proposal) => {
    state.proposal = proposal || null
    replaceSelection(proposal?.status === 'READY'
      ? proposal.operations.map((operation) => operation.operationKey)
      : [])
    confirmAttempt = null
    rejectAttempt = null
  }

  const refreshItinerary = async () => {
    if (!itineraryApi?.get) return null
    const snapshot = await itineraryApi.get(id)
    state.itinerarySnapshot = snapshot
    return snapshot
  }

  const planning = {
    state,
    async load() {
      state.error = null
      try {
        state.request = await api.getRequest(id)
        state.proposals = await api.listProposals(id)
        const ready = state.proposals.find((entry) => entry.status === 'READY')
        setProposal(ready || state.proposals[0] || null)
        state.status = ready ? 'ready' : 'idle'
        return state.request
      } catch (error) {
        if (error?.errorCode === 'PLANNING_NOT_FOUND') {
          state.request = null
          state.proposals = []
          setProposal(null)
          state.status = 'idle'
          return null
        }
        state.status = 'failed'
        state.error = error
        throw error
      }
    },
    saveDraft(draft) {
      if (saving) return saving
      state.status = 'saving'
      state.error = null
      const command = {
        requestId: state.request?.id ?? null,
        expectedVersion: state.request?.version ?? 0,
        draft: requestDraft(draft),
      }
      saving = api.saveRequest(id, command)
        .then((saved) => {
          state.request = saved
          state.status = 'idle'
          return saved
        })
        .catch((error) => {
          state.status = errorState(error)
          state.error = error
          throw error
        })
        .finally(() => { saving = null })
      return saving
    },
    generate() {
      if (generating) return generating
      if (!state.request) return Promise.reject(new Error('请先保存规划需求'))
      state.status = 'generating'
      state.error = null
      generating = api.generate(id, { expectedVersion: state.request.version })
        .then(async (generated) => {
          state.proposals = [generated, ...state.proposals.filter((entry) => entry.id !== generated.id)]
          setProposal(generated)
          state.request = await api.getRequest(id)
          if (generated.status === 'READY') {
            state.status = 'ready'
          } else {
            state.status = 'failed'
            state.error = Object.assign(new Error('AI 建议生成失败'), {
              errorCode: generated.failureCode || 'INVALID_CONTRACT',
            })
          }
          return generated
        })
        .catch((error) => {
          state.status = errorState(error)
          state.error = error
          throw error
        })
        .finally(() => { generating = null })
      return generating
    },
    openProposal(proposalId) {
      const proposal = state.proposals.find((entry) => entry.id === String(proposalId))
      if (!proposal) return null
      setProposal(proposal)
      state.status = proposal.status === 'READY' ? 'ready' : 'idle'
      return proposal
    },
    selectOperation(operationKey, selected) {
      const operations = new Map(
        (state.proposal?.operations || []).map((operation) => [operation.operationKey, operation]),
      )
      if (!operations.has(operationKey)) return
      if (selected) {
        const include = (key) => {
          if (state.selectedOperationKeys.has(key)) return
          for (const dependency of operations.get(key)?.dependencies || []) include(dependency)
          state.selectedOperationKeys.add(key)
        }
        include(operationKey)
      } else {
        state.selectedOperationKeys.delete(operationKey)
        let changed = true
        while (changed) {
          changed = false
          for (const operation of operations.values()) {
            if (state.selectedOperationKeys.has(operation.operationKey)
              && (operation.dependencies || []).some((dependency) => !state.selectedOperationKeys.has(dependency))) {
              state.selectedOperationKeys.delete(operation.operationKey)
              changed = true
            }
          }
        }
      }
      confirmAttempt = null
    },
    confirm() {
      if (confirming) return confirming
      if (!state.proposal || state.selectedOperationKeys.size === 0) {
        return Promise.reject(new Error('请至少选择一项建议'))
      }
      const selectedOperationKeys = [...state.selectedOperationKeys].sort()
      const signature = `${state.proposal.id}:${state.proposal.baseItineraryVersion}:${selectedOperationKeys.join(',')}`
      if (!confirmAttempt || confirmAttempt.signature !== signature) {
        confirmAttempt = {
          signature,
          command: {
            decisionId: uuid(),
            commandId: uuid(),
            expectedItineraryVersion: state.proposal.baseItineraryVersion,
            selectedOperationKeys,
          },
        }
      }
      state.status = 'confirming'
      state.error = null
      confirming = api.confirm(id, state.proposal.id, confirmAttempt.command)
        .then(async (resolution) => {
          await refreshItinerary()
          state.proposal.status = 'CONFIRMED'
          state.status = 'confirmed'
          confirmAttempt = null
          return resolution
        })
        .catch(async (error) => {
          state.status = errorState(error)
          state.error = error
          if (state.status === 'expired') await refreshItinerary()
          throw error
        })
        .finally(() => { confirming = null })
      return confirming
    },
    reject() {
      if (rejecting) return rejecting
      if (!state.proposal) return Promise.reject(new Error('请选择一条建议'))
      if (!rejectAttempt || rejectAttempt.proposalId !== state.proposal.id) {
        rejectAttempt = { proposalId: state.proposal.id, command: { decisionId: uuid() } }
      }
      state.status = 'confirming'
      state.error = null
      rejecting = api.reject(id, state.proposal.id, rejectAttempt.command)
        .then((resolution) => {
          state.proposal.status = 'REJECTED'
          state.status = 'idle'
          rejectAttempt = null
          return resolution
        })
        .catch((error) => {
          state.status = errorState(error)
          state.error = error
          throw error
        })
        .finally(() => { rejecting = null })
      return rejecting
    },
  }
  return planning
}
