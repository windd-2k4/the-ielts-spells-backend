# Deterministic AI Speaking Examiner contract (Phase 4)

`AI_SPEAKING_EXAMINER_ENABLED` is disabled by default. The following student
endpoints return `404 AI_SPEAKING_EXAMINER_DISABLED` while it is off.

Standalone Part 2 is additionally guarded by `AI_SPEAKING_PART_2_ENABLED`
(disabled by default). While it is off, creating `variant=PART_2` returns
`422 SPEAKING_PART_2_DISABLED`; the Part 2 segment inside `FULL_MOCK` is not
affected.

Standalone Part 3 is guarded independently by `AI_SPEAKING_PART_3_ENABLED`
(disabled by default). While it is off, creating `variant=PART_3` returns
`422 SPEAKING_PART_3_DISABLED`; `FULL_MOCK` remains unaffected.

## Session lifecycle

```text
FULL_MOCK: CREATED -> MIC_CHECK -> IDENTITY_CHECK -> PART_1 -> PART_2_PREPARATION
           -> PART_2_LONG_TURN -> PART_2_FOLLOW_UP -> PART_3 -> CLOSING
           -> EVALUATION_QUEUED
PART_1:    CREATED -> MIC_CHECK -> PART_1 -> CLOSING -> EVALUATION_QUEUED
PART_2:    CREATED -> MIC_CHECK -> PART_2_PREPARATION -> PART_2_LONG_TURN
           -> PART_2_FOLLOW_UP -> CLOSING -> EVALUATION_QUEUED
PART_3:    CREATED -> MIC_CHECK -> PART_3 -> CLOSING -> EVALUATION_QUEUED
```

`ABORTED` is terminal and is allowed from every non-terminal state. Part 1 and
Part 3 may remain in the same state while advancing to their next preplanned
question. Every accepted action increments `stateVersion`.

Abort semantics are explicit: aborting closes the examiner session permanently
(`ABORTED`), but does not submit or cancel the outer test attempt. The attempt
stays `IN_PROGRESS`; the existing restart flow may expire it and create a new
attempt. The aborted examiner session rejects further turn and ready actions
with `409`; repeating abort is idempotent and returns the same terminal
snapshot.

For `FULL_MOCK`, the server selects exactly three Part 1 prompts, one Part 2
cue card, one deterministic Part 2 follow-up, and the linked Part 3 prompts.
The complete selected route is persisted in `speaking_exam_turns`; refresh and
resume never re-plan it or call an LLM. Full Mock Part 2 and Part 3 share one
`topicKey`.

## API

All paths are under `/api/v1/student/speaking` and require an assessment-attempt
student JWT.

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/attempts/{attemptId}/exam-session` | Create or resume the one session for the attempt. |
| `GET` | `/exam-sessions/{sessionId}` | Recover state and current/next turn after refresh. |
| `POST` | `/exam-sessions/{sessionId}/turns` | Submit one recorded turn. |
| `POST` | `/exam-sessions/{sessionId}/part-2/ready` | Move preparation to the Part 2 long turn. |
| `POST` | `/exam-sessions/{sessionId}/abort` | Abort a non-terminal session. |
| `GET` | `/exam-sessions/{sessionId}/turns/{turnId}/audio` | Stream private examiner prompt audio. |

`POST .../exam-session` accepts an optional body. Omitting the body or
`variant` preserves the legacy `FULL_MOCK` behavior:

```json
{ "variant": "PART_1" }
```

Supported variants are `FULL_MOCK`, `PART_1`, `PART_2`, and `PART_3`. The
session response includes the immutable `variant`, derived `partsIncluded`,
and `overallBandAvailable` (`true` only for `FULL_MOCK`). Resuming with a
different variant returns `409 SPEAKING_EXAMINER_VARIANT_CONFLICT`.

Forecast is a catalog/content classification, not a fifth state-machine route.
Clients list published forecast banks through the existing practice catalog
using `skill=SPEAKING&format=FORECAST`, start an attempt for the selected bank,
then create `PART_1`, `PART_2`, or `PART_3` using the request above. The catalog
matches either `builder_content.format=FORECAST` or the published `FORECAST`
tag. This keeps evaluation scope and resume semantics identical to normal
part practice and avoids a parallel Forecast session model.

The planner snapshots only the selected route. Standalone Part 1 contains
3–6 banked questions, Part 2 contains one cue card and one banked follow-up,
and Part 3 contains 3–5 banked questions. A missing or incomplete selected
part returns `422 BUSINESS_RULE_VIOLATION`. Standalone routes omit the identity
check and never create turns for unselected parts.

Published Speaking full tests asynchronously pre-warm every deterministic TTS
prompt after the publish transaction commits. A cache/provider failure is
logged and does not roll back publication; session creation retains the lazy
cache fallback. Turn processing emits structured timing fields for audio
duration, private-storage read, STT processing, real-time factor, and total
turn latency.

`POST .../turns` body:

```json
{
  "clientTurnId": "uuid",
  "examinerTurnId": "uuid",
  "stateVersion": 4,
  "storageKey": "speaking-attempts/{attemptId}/{uuid}.webm",
  "transcript": "optional client telemetry",
  "durationMs": 18420
}
```

The server validates the storage key against the authenticated attempt and
turn, measures audio duration from the stored asset, and re-runs STT. The
client transcript and VAD signal are never scoring evidence. Reusing the same
`clientTurnId` returns the exact original accepted response, including the
original `nextTurn` snapshot, even if the session has advanced since the first
request. A different request with a stale `stateVersion` returns
`409 SPEAKING_EXAMINER_STATE_CONFLICT`.

`POST .../part-2/ready` and `POST .../abort` each accept:

```json
{ "stateVersion": 4 }
```

No feedback, correction, score, or adaptive follow-up is emitted during this
milestone. Evaluation is queued only after the closing acknowledgement, and
future scores remain `ESTIMATED_BAND` rather than official IELTS results.

When the closing acknowledgement transitions the session to
`EVALUATION_QUEUED`, the server sets `completed_at` once and keeps it stable on
refresh and idempotent replay.
