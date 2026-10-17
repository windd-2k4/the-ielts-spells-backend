-- Idempotent Reading UI fixture.
-- Prerequisite: Flyway migrations have been applied and an active ADMIN exists.
-- Creates one published, self-practice Reading test for every supported type.

begin;

create temporary table seed_reading_ui_context (
    admin_id uuid primary key
) on commit drop;

insert into seed_reading_ui_context (admin_id)
select p.id
from public.profiles p
join public.user_roles r on r.user_id = p.id
where r.role = 'ADMIN'
  and p.is_active = true
order by p.created_at
limit 1;

do $$
begin
    if not exists (select 1 from seed_reading_ui_context) then
        raise exception
            'Khong tim thay tai khoan ADMIN dang hoat dong de tao Reading UI seed.';
    end if;
end
$$;

create temporary table seed_reading_ui_data (
    test_id uuid primary key,
    version_id uuid not null,
    code text not null unique,
    type_format text not null,
    title text not null,
    prompt text not null,
    correct_answers jsonb not null,
    question_options jsonb not null,
    shared_options jsonb not null,
    answer_source text,
    allow_option_reused boolean,
    required_answer_count smallint,
    word_limit_rule text,
    gap_fill_template text,
    passage_content text not null
) on commit drop;

insert into seed_reading_ui_data (
    test_id, version_id, code, type_format, title, prompt, correct_answers,
    question_options, shared_options, answer_source, allow_option_reused,
    required_answer_count, word_limit_rule, gap_fill_template, passage_content
)
values
    ('a1000000-0000-4000-8000-000000000001', 'b2000000-0000-4000-8000-000000000001', 'TST-SEED-R-UI-01', 'MULTIPLE_CHOICE', 'UI Demo · Reading · Multiple Choice', 'Which resource did the garden store underground?', '["option-a"]', '[{"id":"option-a","code":"A","text":"Water"},{"id":"option-b","code":"B","text":"Sand"},{"id":"option-c","code":"C","text":"Glass"},{"id":"option-d","code":"D","text":"Fuel"}]', '[]', 'PASSAGE', null, null, null, null, 'The small rooftop garden stored water underground. Volunteers used a light roof to protect seedlings and recorded results each Friday.'),
    ('a1000000-0000-4000-8000-000000000002', 'b2000000-0000-4000-8000-000000000002', 'TST-SEED-R-UI-02', 'MULTIPLE_ANSWERS', 'UI Demo · Reading · Multiple Answers', 'Which TWO actions helped the rooftop garden?', '["option-a","option-b"]', '[{"id":"option-a","code":"A","text":"Storing water"},{"id":"option-b","code":"B","text":"Recording results"},{"id":"option-c","code":"C","text":"Removing the roof"},{"id":"option-d","code":"D","text":"Painting the soil"}]', '[]', 'PASSAGE', null, 2, null, null, 'The small rooftop garden stored water underground. Volunteers used a light roof to protect seedlings and recorded results each Friday.'),
    ('a1000000-0000-4000-8000-000000000003', 'b2000000-0000-4000-8000-000000000003', 'TST-SEED-R-UI-03', 'TRUE_FALSE_NOT_GIVEN', 'UI Demo · Reading · True False Not Given', 'The volunteers recorded results every Friday.', '["TRUE"]', '[]', '[]', 'PASSAGE', null, null, null, null, 'The small rooftop garden stored water underground. Volunteers used a light roof to protect seedlings and recorded results each Friday.'),
    ('a1000000-0000-4000-8000-000000000004', 'b2000000-0000-4000-8000-000000000004', 'TST-SEED-R-UI-04', 'YES_NO_NOT_GIVEN', 'UI Demo · Reading · Yes No Not Given', 'The project was designed by a national government.', '["NO"]', '[]', '[]', 'PASSAGE', null, null, null, null, 'The small rooftop garden stored water underground. Volunteers used a light roof to protect seedlings and recorded results each Friday. The passage does not name a national government as its designer.'),
    ('a1000000-0000-4000-8000-000000000005', 'b2000000-0000-4000-8000-000000000005', 'TST-SEED-R-UI-05', 'MATCHING_HEADINGS', 'UI Demo · Reading · Matching Headings', 'Choose the heading that best describes the paragraph.', '["shared-a"]', '[]', '[{"id":"shared-a","code":"A","text":"A practical water-saving idea"},{"id":"shared-b","code":"B","text":"A history of city roofs"},{"id":"shared-c","code":"C","text":"A problem with winter travel"},{"id":"shared-d","code":"D","text":"A volunteer training plan"},{"id":"shared-e","code":"E","text":"A guide to rooftop lighting"}]', 'OPTION_BANK', false, null, null, null, 'The small rooftop garden stored water underground. Volunteers used a light roof to protect seedlings and recorded results each Friday.'),
    ('a1000000-0000-4000-8000-000000000006', 'b2000000-0000-4000-8000-000000000006', 'TST-SEED-R-UI-06', 'MATCHING_INFORMATION', 'UI Demo · Reading · Matching Information', 'Which paragraph contains information about storing water?', '["shared-a"]', '[]', '[{"id":"shared-a","code":"A","text":"Paragraph A"},{"id":"shared-b","code":"B","text":"Paragraph B"},{"id":"shared-c","code":"C","text":"Paragraph C"},{"id":"shared-d","code":"D","text":"Paragraph D"},{"id":"shared-e","code":"E","text":"Paragraph E"}]', 'OPTION_BANK', true, null, null, null, 'The small rooftop garden stored water underground. Volunteers used a light roof to protect seedlings and recorded results each Friday.'),
    ('a1000000-0000-4000-8000-000000000007', 'b2000000-0000-4000-8000-000000000007', 'TST-SEED-R-UI-07', 'MATCHING_FEATURES', 'UI Demo · Reading · Matching Features', 'Which person introduced the weekly record?', '["shared-a"]', '[]', '[{"id":"shared-a","code":"A","text":"Mina, the coordinator"},{"id":"shared-b","code":"B","text":"Owen, the architect"},{"id":"shared-c","code":"C","text":"Linh, the visitor"},{"id":"shared-d","code":"D","text":"Bao, the gardener"},{"id":"shared-e","code":"E","text":"An, the researcher"}]', 'OPTION_BANK', true, null, null, null, 'The small rooftop garden stored water underground. Mina, the coordinator, introduced a simple record sheet, and volunteers used it each Friday.'),
    ('a1000000-0000-4000-8000-000000000008', 'b2000000-0000-4000-8000-000000000008', 'TST-SEED-R-UI-08', 'MATCHING_SENTENCE_ENDINGS', 'UI Demo · Reading · Matching Sentence Endings', 'The garden became easier to manage because...', '["shared-a"]', '[]', '[{"id":"shared-a","code":"A","text":"volunteers recorded results"},{"id":"shared-b","code":"B","text":"the roof was removed"},{"id":"shared-c","code":"C","text":"the soil became glass"},{"id":"shared-d","code":"D","text":"the garden moved indoors"},{"id":"shared-e","code":"E","text":"the project stopped early"}]', 'OPTION_BANK', false, null, null, null, 'The small rooftop garden stored water underground. Volunteers used a light roof to protect seedlings and recorded results each Friday.'),
    ('a1000000-0000-4000-8000-000000000009', 'b2000000-0000-4000-8000-000000000009', 'TST-SEED-R-UI-09', 'FILL_IN_BLANK', 'UI Demo · Reading · Fill in the Blank', 'What did the garden store underground?', '["water"]', '[]', '[]', 'PASSAGE', null, null, 'NO MORE THAN TWO WORDS', 'The rooftop garden stored [[1]], [[2]], [[3]], [[4]], and [[5]] underground.', 'The small rooftop garden stored water underground. Volunteers used a light roof to protect seedlings and recorded results each Friday.'),
    ('a1000000-0000-4000-8000-000000000010', 'b2000000-0000-4000-8000-000000000010', 'TST-SEED-R-UI-10', 'SHORT_ANSWER', 'UI Demo · Reading · Short Answer', 'What protected the seedlings?', '["a light roof"]', '[]', '[]', 'PASSAGE', null, null, 'NO MORE THAN THREE WORDS', null, 'The small rooftop garden stored water underground. Volunteers used a light roof to protect seedlings and recorded results each Friday.'),
    ('a1000000-0000-4000-8000-000000000011', 'b2000000-0000-4000-8000-000000000011', 'TST-SEED-R-UI-11', 'SENTENCE_COMPLETION', 'UI Demo · Reading · Sentence Completion', 'Complete the sentence from the passage.', '["water"]', '[]', '[]', 'PASSAGE', null, null, 'NO MORE THAN TWO WORDS', 'The garden stored [[1]], [[2]], [[3]], [[4]], and [[5]] underground.', 'The small rooftop garden stored water underground. Volunteers used a light roof to protect seedlings and recorded results each Friday.'),
    ('a1000000-0000-4000-8000-000000000012', 'b2000000-0000-4000-8000-000000000012', 'TST-SEED-R-UI-12', 'SUMMARY_COMPLETION', 'UI Demo · Reading · Summary Completion', 'Complete the summary with a word from the passage.', '["water"]', '[]', '[]', 'PASSAGE', null, null, 'NO MORE THAN TWO WORDS', 'Summary:\nThe rooftop garden stored [[1]], [[2]], [[3]], [[4]], and [[5]] below the soil.', 'The small rooftop garden stored water underground. Volunteers used a light roof to protect seedlings and recorded results each Friday.'),
    ('a1000000-0000-4000-8000-000000000013', 'b2000000-0000-4000-8000-000000000013', 'TST-SEED-R-UI-13', 'NOTE_COMPLETION', 'UI Demo · Reading · Note Completion', 'Complete the note with a word from the passage.', '["water"]', '[]', '[]', 'PASSAGE', null, null, 'NO MORE THAN TWO WORDS', 'Notes:\nResource 1: [[1]]\nResource 2: [[2]]\nResource 3: [[3]]\nResource 4: [[4]]\nResource 5: [[5]]', 'The small rooftop garden stored water underground. Volunteers used a light roof to protect seedlings and recorded results each Friday.'),
    ('a1000000-0000-4000-8000-000000000014', 'b2000000-0000-4000-8000-000000000014', 'TST-SEED-R-UI-14', 'TABLE_COMPLETION', 'UI Demo · Reading · Table Completion', 'Complete the table with a word from the passage.', '["water"]', '[]', '[]', 'PASSAGE', null, null, 'NO MORE THAN TWO WORDS', '| Feature | Finding |\n| --- | --- |\n| Resource 1 | [[1]] |\n| Resource 2 | [[2]] |\n| Resource 3 | [[3]] |\n| Resource 4 | [[4]] |\n| Resource 5 | [[5]] |', 'The small rooftop garden stored water underground. Volunteers used a light roof to protect seedlings and recorded results each Friday.'),
    ('a1000000-0000-4000-8000-000000000015', 'b2000000-0000-4000-8000-000000000015', 'TST-SEED-R-UI-15', 'FLOW_CHART_COMPLETION', 'UI Demo · Reading · Flow-chart Completion', 'Complete the final step in the process.', '["water"]', '[]', '[]', 'PASSAGE', null, null, 'NO MORE THAN TWO WORDS', 'Collect samples: [[1]].\n\nProtect seedlings: [[2]].\n\nRecord resource: [[3]].\n\nReview notes: [[4]].\n\nPublish result: [[5]].', 'The small rooftop garden stored water underground. Volunteers used a light roof to protect seedlings and recorded results each Friday.'),
    ('a1000000-0000-4000-8000-000000000016', 'b2000000-0000-4000-8000-000000000016', 'TST-SEED-R-UI-16', 'DIAGRAM_LABELING', 'UI Demo · Reading · Diagram Labeling', 'Which letter labels the underground water tank?', '["diagram-a"]', '[]', '[{"id":"diagram-a","code":"A","text":"Underground water tank"},{"id":"diagram-b","code":"B","text":"Seedling roof"},{"id":"diagram-c","code":"C","text":"Record desk"},{"id":"diagram-d","code":"D","text":"Sample shelf"},{"id":"diagram-e","code":"E","text":"Entry gate"}]', 'OPTION_BANK', false, null, null, null, 'The small rooftop garden stored water underground. Volunteers used a light roof to protect seedlings and recorded results each Friday.' );

create temporary table seed_reading_ui_payload on commit drop as
select
    d.*,
    jsonb_build_object(
        'format', 'PRACTICE',
        'passages', jsonb_build_array(
            jsonb_build_object(
                'id', 'ui-passage-' || lower(d.code),
                'passageNo', 1,
                'title', 'The rooftop garden experiment',
                'content', d.passage_content,
                'questionGroups', jsonb_build_array(
                    jsonb_strip_nulls(jsonb_build_object(
                        'id', 'ui-group-' || lower(d.code),
                        'title', 'Questions 1–5 · ' || replace(d.type_format, '_', ' '),
                        'typeFormat', d.type_format,
                        'instructions', case
                            when d.type_format = 'MULTIPLE_CHOICE' then 'Choose the correct letter, A, B, C or D.'
                            when d.type_format = 'MULTIPLE_ANSWERS' then 'Choose TWO letters, A–D.'
                            when d.type_format = 'TRUE_FALSE_NOT_GIVEN' then 'Choose TRUE, FALSE or NOT GIVEN.'
                            when d.type_format = 'YES_NO_NOT_GIVEN' then 'Choose YES, NO or NOT GIVEN.'
                            when d.type_format like 'MATCHING_%' then 'Choose the correct option from the list.'
                            when d.type_format = 'DIAGRAM_LABELING' then 'Choose the correct letter, A–E.'
                            else 'Complete the answer using the words from the passage.'
                        end,
                        'answerSource', d.answer_source,
                        'allowOptionReused', d.allow_option_reused,
                        'requiredAnswerCount', d.required_answer_count,
                        'wordLimitRule', d.word_limit_rule,
                        'gapFillTemplate', d.gap_fill_template,
                        'sharedOptions', d.shared_options,
                        'questions', (
                            select jsonb_agg(
                                jsonb_strip_nulls(jsonb_build_object(
                                    'id', 'ui-question-' || lower(d.code) || '-' || q.question_no,
                                    'number', q.question_no,
                                    'typeFormat', d.type_format,
                                    'prompt', d.prompt || ' · Item ' || q.question_no,
                                    'options', case
                                        when d.type_format in ('MULTIPLE_CHOICE', 'MULTIPLE_ANSWERS') then jsonb_build_array(
                                            jsonb_build_object('id', 'option-' || q.question_no || '-a', 'code', 'A', 'text', 'Water'),
                                            jsonb_build_object('id', 'option-' || q.question_no || '-b', 'code', 'B', 'text', 'A weekly record'),
                                            jsonb_build_object('id', 'option-' || q.question_no || '-c', 'code', 'C', 'text', 'A glass roof'),
                                            jsonb_build_object('id', 'option-' || q.question_no || '-d', 'code', 'D', 'text', 'A fuel tank')
                                        )
                                        else d.question_options
                                    end,
                                    'correctAnswers', case
                                        when d.type_format = 'MULTIPLE_CHOICE' then jsonb_build_array('option-' || q.question_no || '-a')
                                        when d.type_format = 'MULTIPLE_ANSWERS' then jsonb_build_array('option-' || q.question_no || '-a', 'option-' || q.question_no || '-b')
                                        when d.type_format = 'TRUE_FALSE_NOT_GIVEN' then jsonb_build_array(case q.question_no when 1 then 'TRUE' when 2 then 'FALSE' when 3 then 'NOT_GIVEN' when 4 then 'TRUE' else 'FALSE' end)
                                        when d.type_format = 'YES_NO_NOT_GIVEN' then jsonb_build_array(case q.question_no when 1 then 'YES' when 2 then 'NO' when 3 then 'NOT_GIVEN' when 4 then 'YES' else 'NO' end)
                                        when d.type_format in ('MATCHING_HEADINGS', 'MATCHING_INFORMATION', 'MATCHING_FEATURES', 'MATCHING_SENTENCE_ENDINGS') then jsonb_build_array('shared-' || chr(96 + q.question_no))
                                        when d.type_format = 'DIAGRAM_LABELING' then jsonb_build_array('diagram-' || chr(96 + q.question_no))
                                        else d.correct_answers
                                    end,
                                    'acceptableAnswers', case
                                        when d.type_format in ('FILL_IN_BLANK', 'SHORT_ANSWER', 'SENTENCE_COMPLETION', 'SUMMARY_COMPLETION', 'NOTE_COMPLETION', 'TABLE_COMPLETION', 'FLOW_CHART_COMPLETION') then d.correct_answers
                                        else '[]'::jsonb
                                    end,
                                    'maxScore', 1,
                                    'solution', jsonb_build_object(
                                        'explanation', 'The passage points to the matching detail for item ' || q.question_no || '.',
                                        'reasoningSteps', jsonb_build_array('Locate the matching detail in the passage.', 'Check the answer against the question stem.'),
                                        'solutionVisibility', 'STUDENT_AFTER_SUBMIT'
                                    )
                                )) order by q.question_no)
                            from generate_series(1, 5) as q(question_no)
                        )
                    ))
                )
            )
        ),
        'seedKey', d.code
    ) as builder_content
from seed_reading_ui_data d;

insert into public.tests (
    id, code, title, description, duration_minutes, status, created_by,
    primary_skill, test_type, version, tags, builder_content
)
select
    p.test_id,
    p.code,
    p.title,
    'Five-question published fixture for checking the Reading UI.',
    10,
    'PUBLISHED',
    c.admin_id,
    'READING',
    'SINGLE_SKILL',
    'v2.0',
    jsonb_build_array('seed', 'reading', 'ui', p.type_format),
    p.builder_content
from seed_reading_ui_payload p
cross join seed_reading_ui_context c
on conflict (code) do update set
    title = excluded.title,
    description = excluded.description,
    duration_minutes = excluded.duration_minutes,
    status = excluded.status,
    created_by = excluded.created_by,
    primary_skill = excluded.primary_skill,
    test_type = excluded.test_type,
    version = excluded.version,
    tags = excluded.tags,
    builder_content = excluded.builder_content,
    updated_at = now();

insert into public.test_versions (
    id, test_id, version_number, version_label, title, description,
    duration_minutes, primary_skill, test_type, tags, builder_content,
    published_by, published_at
)
select
    p.version_id,
    p.test_id,
    2,
    'v2.0',
    p.title,
    'Five-question published fixture for checking the Reading UI.',
    10,
    'READING',
    'SINGLE_SKILL',
    jsonb_build_array('seed', 'reading', 'ui', p.type_format),
    p.builder_content,
    c.admin_id,
    now()
from seed_reading_ui_payload p
cross join seed_reading_ui_context c
on conflict (test_id, version_number) do nothing;

update public.tests test
set status = 'PUBLISHED',
    current_published_version_id = p.version_id,
    version = 'v2.0',
    updated_at = now()
from seed_reading_ui_payload p
where test.id = p.test_id;

-- Rebuild only the materialized rows owned by these demo versions. Published
-- versions remain immutable; their delivery rows can be recreated safely.
delete from public.test_version_sections section
using seed_reading_ui_payload p
where section.test_version_id = p.version_id;

insert into public.test_version_sections (
    id, test_version_id, section_key, section_no, title, content_html, display_order
)
select
    gen_random_uuid(),
    p.version_id,
    passage ->> 'id',
    (passage ->> 'passageNo')::smallint,
    passage ->> 'title',
    passage ->> 'content',
    0
from seed_reading_ui_payload p
cross join lateral jsonb_array_elements(p.builder_content -> 'passages') passage;

insert into public.test_version_question_groups (
    id, test_version_id, section_id, group_key, title, type_format,
    instructions, answer_config, display_order
)
select
    gen_random_uuid(),
    p.version_id,
    section.id,
    group_data ->> 'id',
    group_data ->> 'title',
    group_data ->> 'typeFormat',
    group_data ->> 'instructions',
    jsonb_strip_nulls(jsonb_build_object(
        'answerSource', group_data -> 'answerSource',
        'allowOptionReused', group_data -> 'allowOptionReused',
        'requiredAnswerCount', group_data -> 'requiredAnswerCount',
        'wordLimitRule', group_data -> 'wordLimitRule',
        'gapFillTemplate', group_data -> 'gapFillTemplate'
    )),
    0
from seed_reading_ui_payload p
cross join lateral jsonb_array_elements(p.builder_content -> 'passages') passage
cross join lateral jsonb_array_elements(passage -> 'questionGroups') group_data
join public.test_version_sections section
  on section.test_version_id = p.version_id
 and section.section_key = passage ->> 'id';

insert into public.test_version_questions (
    id, test_version_id, group_id, question_key, question_no, type_format,
    prompt, correct_answers, acceptable_answers, explanation, reasoning_steps,
    solution_visibility, max_score, display_order
)
select
    gen_random_uuid(),
    p.version_id,
    question_group.id,
    question_data ->> 'id',
    (question_data ->> 'number')::smallint,
    question_data ->> 'typeFormat',
    question_data ->> 'prompt',
    coalesce(question_data -> 'correctAnswers', '[]'::jsonb),
    coalesce(question_data -> 'acceptableAnswers', '[]'::jsonb),
    question_data -> 'solution' ->> 'explanation',
    coalesce(question_data -> 'solution' -> 'reasoningSteps', '[]'::jsonb),
    coalesce(question_data -> 'solution' ->> 'solutionVisibility', 'STUDENT_AFTER_SUBMIT'),
    coalesce((question_data ->> 'maxScore')::numeric, 1),
    0
from seed_reading_ui_payload p
cross join lateral jsonb_array_elements(p.builder_content -> 'passages') passage
cross join lateral jsonb_array_elements(passage -> 'questionGroups') group_data
cross join lateral jsonb_array_elements(group_data -> 'questions') question_data
join public.test_version_question_groups question_group
  on question_group.test_version_id = p.version_id
 and question_group.group_key = group_data ->> 'id';

insert into public.test_version_question_options (
    test_version_id, group_id, question_id, option_key, option_code,
    content, display_order
)
select
    p.version_id,
    question_group.id,
    null,
    option_data ->> 'id',
    coalesce(option_data ->> 'code', option_data ->> 'label'),
    coalesce(nullif(option_data ->> 'text', ''), option_data ->> 'code', option_data ->> 'label'),
    option_index - 1
from seed_reading_ui_payload p
cross join lateral jsonb_array_elements(p.builder_content -> 'passages') passage
cross join lateral jsonb_array_elements(passage -> 'questionGroups') group_data
cross join lateral jsonb_array_elements(coalesce(group_data -> 'sharedOptions', '[]'::jsonb)) with ordinality option_data(option_data, option_index)
join public.test_version_question_groups question_group
  on question_group.test_version_id = p.version_id
 and question_group.group_key = group_data ->> 'id';

insert into public.test_version_question_options (
    test_version_id, group_id, question_id, option_key, option_code,
    content, display_order
)
select
    p.version_id,
    question_group.id,
    question.id,
    option_data ->> 'id',
    coalesce(option_data ->> 'code', option_data ->> 'label'),
    coalesce(nullif(option_data ->> 'text', ''), option_data ->> 'code', option_data ->> 'label'),
    option_index - 1
from seed_reading_ui_payload p
cross join lateral jsonb_array_elements(p.builder_content -> 'passages') passage
cross join lateral jsonb_array_elements(passage -> 'questionGroups') group_data
cross join lateral jsonb_array_elements(group_data -> 'questions') question_data
cross join lateral jsonb_array_elements(coalesce(question_data -> 'options', '[]'::jsonb)) with ordinality option_data(option_data, option_index)
join public.test_version_question_groups question_group
  on question_group.test_version_id = p.version_id
 and question_group.group_key = group_data ->> 'id'
join public.test_version_questions question
  on question.test_version_id = p.version_id
 and question.question_key = question_data ->> 'id';

commit;
