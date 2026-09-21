# Reading Question Type Compatibility Matrix

This is the contract between the Reading authoring flow, the publish gate, the immutable published snapshot, the student player, the auto-grader, and the explanation view. The test IDs in the last column are the parameterized integration-test rows in `ReadingQuestionTypeCompatibilityIntegrationTest`.

## Compatibility rules

- The authoritative type set is the `ReadingQuestionType` union in `packages/contracts` and the server-side Reading validator. A type is not considered supported until it can pass the publish gate, materialize into a published version, render in student delivery, accept the answer payload, and be auto-graded.
- `MATCHING_*` types use a group-level `sharedOptions` option bank. The question answer is the option key, not the displayed label.
- Completion types use passage text by default and therefore require `wordLimitRule`. They also use `gapFillTemplate` when the answer source is `PASSAGE`.
- `SUMMARY_COMPLETION` supports both `PASSAGE` and `OPTION_BANK`. The primary row tests the passage path; `Q12-OB` is the additional option-bank branch test.
- `MULTIPLE_ANSWERS` is set-based: answer order does not affect correctness, but the submitted set must match the complete expected set.
- Published delivery never exposes `correctAnswers` to the student attempt payload. Correctness and explanations are returned only by the result flow according to `solutionVisibility`.

## Matrix

| ID | Question type | Authoring / publish-gate contract | Published snapshot and student delivery | Answer payload and scoring | Explanation path | Integration coverage |
|---|---|---|---|---|---|---|
| Q01 | `MULTIPLE_CHOICE` | Question-level `options` (at least two), exactly one `correctAnswers` value referencing an option key. | Group and question preserve `typeFormat`; question options are delivered as `{ key, code, text }`. | `{ "value": "option-key" }`; exact single-value match. | Standard question explanation and optional evidence. | Publish → materialize → deliver options → save A → submit = correct. |
| Q02 | `MULTIPLE_ANSWERS` | Question-level `options`; `requiredAnswerCount` 2–6; correct-answer count equals the required count. | Same question options; group preserves `requiredAnswerCount`. | `{ "values": ["key-a", "key-b"] }`; normalized set equality (order-independent). | Standard explanation. | Save reversed answer order → submit = correct; proves set semantics. |
| Q03 | `TRUE_FALSE_NOT_GIVEN` | Correct value must be `TRUE`, `FALSE`, or `NOT_GIVEN`. | Type and instructions are preserved; no option bank required. | `{ "value": "TRUE" }`; normalized scalar match. | Standard explanation/evidence. | Publish and grade a `TRUE` response. |
| Q04 | `YES_NO_NOT_GIVEN` | Correct value must be `YES`, `NO`, or `NOT_GIVEN`. | Type and instructions are preserved; no option bank required. | `{ "value": "YES" }`; normalized scalar match. | Standard explanation/evidence. | Publish and grade a `YES` response. |
| Q05 | `MATCHING_HEADINGS` | Non-empty group `sharedOptions`; each answer references a shared option key. | `sharedOptions` is delivered once at group level; questions carry no duplicated answer key. | `{ "value": "shared-option-key" }`; scalar key match. | Standard matching explanation. | Publish → shared bank delivery → save shared key → correct result. |
| Q06 | `MATCHING_INFORMATION` | Same shared option-bank contract as other matching types. | Shared option bank is materialized and delivered. | Scalar shared-option key; exact match. | Standard matching explanation. | End-to-end publish and grade. |
| Q07 | `MATCHING_FEATURES` | Same shared option-bank contract as other matching types. | Shared option bank is materialized and delivered. | Scalar shared-option key; exact match. | Standard matching explanation. | End-to-end publish and grade. |
| Q08 | `MATCHING_SENTENCE_ENDINGS` | Same shared option-bank contract as other matching types. | Shared option bank is materialized and delivered. | Scalar shared-option key; exact match. | Standard matching explanation. | End-to-end publish and grade. |
| Q09 | `FILL_IN_BLANK` | Passage answer source requires `wordLimitRule` and one `[[n]]` gap in `gapFillTemplate`. | Group answer configuration is preserved for the player; no answer choices are required. | `{ "value": "water" }`; normalized text match plus acceptable answers. | Completion explanation/evidence. | Publish config + save text answer + correct result. |
| Q10 | `SHORT_ANSWER` | Passage answer source requires `wordLimitRule`; no option bank. | Type and word-limit configuration are delivered. | Scalar text value; whitespace/case/punctuation normalization. | Completion explanation/evidence. | Publish config + save text answer + correct result. |
| Q11 | `SENTENCE_COMPLETION` | Passage answer source requires `wordLimitRule` and a numbered gap template. | Type and gap configuration are delivered. | Scalar text value; normalized text match. | Completion explanation/evidence. | Publish config + save text answer + correct result. |
| Q12 | `SUMMARY_COMPLETION` (passage) | Passage path requires `wordLimitRule` and a numbered gap template. | Passage completion configuration is preserved. | Scalar text value; normalized text match. | Completion explanation/evidence. | Publish config + save text answer + correct result. |
| Q13 | `NOTE_COMPLETION` | Passage answer source requires `wordLimitRule` and a numbered gap template. | Type and gap configuration are delivered. | Scalar text value; normalized text match. | Completion explanation/evidence. | Publish config + save text answer + correct result. |
| Q14 | `TABLE_COMPLETION` | Passage answer source requires `wordLimitRule` and a numbered gap template. | Type and gap configuration are delivered. | Scalar text value; normalized text match. | Completion explanation/evidence. | Publish config + save text answer + correct result. |
| Q15 | `FLOW_CHART_COMPLETION` | Passage answer source requires `wordLimitRule` and a numbered gap template. | Type and gap configuration are delivered. | Scalar text value; normalized text match. | Completion explanation/evidence. | Publish config + save text answer + correct result. |
| Q16 | `DIAGRAM_LABELING` | Passage answer source requires `wordLimitRule` and a numbered gap template. | Type, gap configuration, and optional illustration metadata are preserved. | Scalar text value; normalized text match. | Completion explanation/evidence. | Publish config + save text answer + correct result. |
| Q12-OB | `SUMMARY_COMPLETION` (`OPTION_BANK`) | Requires at least two `sharedOptions`; no passage word-limit/gap requirement for the option-bank branch. | Shared options are delivered at group level and `answerConfig.answerSource` is `OPTION_BANK`. | Scalar shared-option key; exact match. | Completion/matching explanation. | Additional branch test for the second Summary Completion mode. |

## What each integration row proves

Every row performs the same production-shaped workflow:

1. Create a Reading draft with the row's real builder payload.
2. Publish it through `TestBankApplicationService.changeStatus`, so the authoritative publish gate runs.
3. Assert the immutable version and materialized question type/configuration in PostgreSQL.
4. Start a self-practice attempt from the published version and inspect the student-safe delivery payload.
5. Save the answer through `StudentReadingDeliveryService.saveResponses` using the same JSON shape as the main web.
6. Submit through `StudentReadingDeliveryService.submit` and assert one correct, zero incorrect, and zero unanswered.

The matrix intentionally tests one representative question per type. Full-test cardinality (3 passages, 40 questions, 60 minutes), draft/revision conflicts, assignment pinning, and negative validation cases remain covered by `ReadingPublishGateIntegrationTest` and `ReadingDraftValidatorTests`.

