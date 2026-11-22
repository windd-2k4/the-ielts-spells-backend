-- User-provided Listening practices. Keep these as drafts until audio,
-- transcripts, and answer keys are supplied in the Listening Builder.
do $migration$
declare
  actor_id uuid;
begin
  select id into actor_id
  from public.profiles
  order by created_at, id
  limit 1;

  if actor_id is not null then
    insert into public.tests(
      title, description, duration_minutes, status, created_by,
      primary_skill, test_type, version, tags, builder_content
    )
    select
      'Practice 1. Vacuum cleaner',
      'User-provided Listening practice. Audio, transcript and answer key are pending.',
      10, 'DRAFT', actor_id, 'LISTENING', 'SINGLE_SKILL', 'v1.0',
      '["PRACTICE", "LISTENING", "SECTION_1", "PENDING_AUDIO", "PENDING_ANSWER_KEY"]'::jsonb,
      $$
      {
        "format": "SECTION_1",
        "sectionsPreset": "SECTION_1",
        "expectedQuestions": 5,
        "parts": [{
          "id": "practice-1-part-1",
          "partNo": 1,
          "title": "Vacuum cleaner",
          "audioUrl": null,
          "audioFilename": null,
          "audioDurationSeconds": null,
          "transcriptHtml": "",
          "questionGroups": [{
            "id": "practice-1-group-1",
            "title": "Questions 11–15",
            "titleMode": "CUSTOM",
            "startQuestionNo": 1,
            "endQuestionNo": 5,
            "typeFormat": "MULTIPLE_CHOICE",
            "instructions": "Choose the correct answer.",
            "answerSource": "PASSAGE",
            "questions": [
              {"id":"practice-1-q1","number":1,"typeFormat":"MULTIPLE_CHOICE","prompt":"When did James Brown start collecting vacuum cleaners?","options":[{"id":"practice-1-q1-a","label":"A","text":"4 years old"},{"id":"practice-1-q1-b","label":"B","text":"8 years old"},{"id":"practice-1-q1-c","label":"C","text":"30 years old"}],"correctAnswers":[]},
              {"id":"practice-1-q2","number":2,"typeFormat":"MULTIPLE_CHOICE","prompt":"James can listen to a vacuum cleaner and identify","options":[{"id":"practice-1-q2-a","label":"A","text":"which company made it."},{"id":"practice-1-q2-b","label":"B","text":"what problem it might have."},{"id":"practice-1-q2-c","label":"C","text":"the age of the battery life."}],"correctAnswers":[]},
              {"id":"practice-1-q3","number":3,"typeFormat":"MULTIPLE_CHOICE","prompt":"Before the 1950s, vacuum cleaners cost","options":[{"id":"practice-1-q3-a","label":"A","text":"the same price of an average car"},{"id":"practice-1-q3-b","label":"B","text":"almost a whole year’s salary."},{"id":"practice-1-q3-c","label":"C","text":"about three months’ wages."}],"correctAnswers":[]},
              {"id":"practice-1-q4","number":4,"typeFormat":"MULTIPLE_CHOICE","prompt":"Why did James make a trip to the USA a few years ago?","options":[{"id":"practice-1-q4-a","label":"A","text":"to win an award in Texas"},{"id":"practice-1-q4-b","label":"B","text":"to visit the Kirby Factory"},{"id":"practice-1-q4-c","label":"C","text":"to meet some vacuum collectors"}],"correctAnswers":[]},
              {"id":"practice-1-q5","number":5,"typeFormat":"MULTIPLE_CHOICE","prompt":"On what day is the vacuum cleaner museum open late?","options":[{"id":"practice-1-q5-a","label":"A","text":"Tuesday to Saturday"},{"id":"practice-1-q5-b","label":"B","text":"Monday"},{"id":"practice-1-q5-c","label":"C","text":"Thursday"}],"correctAnswers":[]}
            ]
          }]
        }]
      }
      $$::jsonb
    where not exists (select 1 from public.tests where title = 'Practice 1. Vacuum cleaner');

    insert into public.tests(
      title, description, duration_minutes, status, created_by,
      primary_skill, test_type, version, tags, builder_content
    )
    select
      'Practice 2. Rivermead School: facilities open to the public',
      'User-provided Listening practice. Audio, transcript and answer key are pending.',
      10, 'DRAFT', actor_id, 'LISTENING', 'SINGLE_SKILL', 'v1.0',
      '["PRACTICE", "LISTENING", "SECTION_1", "PENDING_AUDIO", "PENDING_ANSWER_KEY"]'::jsonb,
      $$
      {
        "format": "SECTION_1",
        "sectionsPreset": "SECTION_1",
        "expectedQuestions": 4,
        "parts": [{
          "id": "practice-2-part-1",
          "partNo": 1,
          "title": "Rivermead School: facilities open to the public",
          "audioUrl": null,
          "audioFilename": null,
          "audioDurationSeconds": null,
          "transcriptHtml": "",
          "questionGroups": [{
            "id": "practice-2-group-1",
            "title": "Questions 1–4",
            "titleMode": "CUSTOM",
            "startQuestionNo": 1,
            "endQuestionNo": 4,
            "typeFormat": "MULTIPLE_CHOICE",
            "instructions": "Choose the correct answer.",
            "answerSource": "PASSAGE",
            "questions": [
              {"id":"practice-2-q1","number":1,"typeFormat":"MULTIPLE_CHOICE","prompt":"What is the main aim of the school’s new theatre?","options":[{"id":"practice-2-q1-a","label":"A","text":"to gain an international reputation"},{"id":"practice-2-q1-b","label":"B","text":"to give local people access to good performances"},{"id":"practice-2-q1-c","label":"C","text":"to provide support for talented amateur actors"}],"correctAnswers":[]},
              {"id":"practice-2-q2","number":2,"typeFormat":"MULTIPLE_CHOICE","prompt":"The first season’s theatre programme will include","options":[{"id":"practice-2-q2-a","label":"A","text":"plays by modern writers."},{"id":"practice-2-q2-b","label":"B","text":"displays by Chinese acrobats."},{"id":"practice-2-q2-c","label":"C","text":"concerts performed by schoolchildren."}],"correctAnswers":[]},
              {"id":"practice-2-q3","number":3,"typeFormat":"MULTIPLE_CHOICE","prompt":"Most of theatre’s funding will come from","options":[{"id":"practice-2-q3-a","label":"A","text":"sponsors."},{"id":"practice-2-q3-b","label":"B","text":"government grants."},{"id":"practice-2-q3-c","label":"C","text":"ticket sales."}],"correctAnswers":[]},
              {"id":"practice-2-q4","number":4,"typeFormat":"MULTIPLE_CHOICE","prompt":"Which facility at the school is open to the public on a limited basis only?","options":[{"id":"practice-2-q4-a","label":"A","text":"the sports hall"},{"id":"practice-2-q4-b","label":"B","text":"the museum"},{"id":"practice-2-q4-c","label":"C","text":"the cafeteria"}],"correctAnswers":[]}
            ]
          }]
        }]
      }
      $$::jsonb
    where not exists (select 1 from public.tests where title = 'Practice 2. Rivermead School: facilities open to the public');

    insert into public.tests(
      title, description, duration_minutes, status, created_by,
      primary_skill, test_type, version, tags, builder_content
    )
    select
      'Practice 3. A tour guide from Travel-lite Travel Agency',
      'User-provided Listening practice. Source: https://realieltsexams.com/san-pham/a-tour-guide-from-travel-lite-travel-agency/?view=exam&questionnaire_id=319. Audio, transcript and answer key are pending.',
      10, 'DRAFT', actor_id, 'LISTENING', 'SINGLE_SKILL', 'v1.0',
      '["PRACTICE", "LISTENING", "SECTION_1", "PENDING_AUDIO", "PENDING_ANSWER_KEY"]'::jsonb,
      $$
      {
        "format": "SECTION_1",
        "sectionsPreset": "SECTION_1",
        "expectedQuestions": 2,
        "sourceUrl": "https://realieltsexams.com/san-pham/a-tour-guide-from-travel-lite-travel-agency/?view=exam&questionnaire_id=319",
        "parts": [{
          "id": "practice-3-part-1",
          "partNo": 1,
          "title": "A tour guide from Travel-lite Travel Agency",
          "audioUrl": null,
          "audioFilename": null,
          "audioDurationSeconds": null,
          "transcriptHtml": "",
          "questionGroups": [{
            "id": "practice-3-group-1",
            "title": "Questions 7–8",
            "titleMode": "CUSTOM",
            "startQuestionNo": 1,
            "endQuestionNo": 2,
            "typeFormat": "MULTIPLE_ANSWERS",
            "requiredAnswerCount": 2,
            "instructions": "Choose TWO correct answers.",
            "answerSource": "PASSAGE",
            "questions": [
              {"id":"practice-3-q1","number":1,"typeFormat":"MULTIPLE_ANSWERS","prompt":"Which TWO types of accommodation are available on a weekly basis?","options":[{"id":"practice-3-q1-a","label":"A","text":"lighthouse"},{"id":"practice-3-q1-b","label":"B","text":"hostel"},{"id":"practice-3-q1-c","label":"C","text":"castle"},{"id":"practice-3-q1-d","label":"D","text":"cottages"},{"id":"practice-3-q1-e","label":"E","text":"bed and breakfast"}],"correctAnswers":[]},
              {"id":"practice-3-q2","number":2,"typeFormat":"MULTIPLE_ANSWERS","prompt":"Which TWO types of accommodation are available on a weekly basis?","options":[{"id":"practice-3-q2-a","label":"A","text":"lighthouse"},{"id":"practice-3-q2-b","label":"B","text":"hostel"},{"id":"practice-3-q2-c","label":"C","text":"castle"},{"id":"practice-3-q2-d","label":"D","text":"cottages"},{"id":"practice-3-q2-e","label":"E","text":"bed and breakfast"}],"correctAnswers":[]}
            ]
          }, {
            "id": "practice-3-group-2",
            "title": "Questions 9–10",
            "titleMode": "CUSTOM",
            "startQuestionNo": 3,
            "endQuestionNo": 4,
            "typeFormat": "MULTIPLE_ANSWERS",
            "requiredAnswerCount": 2,
            "instructions": "Choose TWO correct answers.",
            "answerSource": "PASSAGE",
            "questions": [
              {"id":"practice-3-q3","number":3,"typeFormat":"MULTIPLE_ANSWERS","prompt":"Which TWO benefits can all the members get?","options":[{"id":"practice-3-q3-a","label":"A","text":"free entry to some castles"},{"id":"practice-3-q3-b","label":"B","text":"subscription of Scottish magazines"},{"id":"practice-3-q3-c","label":"C","text":"discount of apartment rent"},{"id":"practice-3-q3-d","label":"D","text":"free visitor guide"},{"id":"practice-3-q3-e","label":"E","text":"free parking"}],"correctAnswers":[]},
              {"id":"practice-3-q4","number":4,"typeFormat":"MULTIPLE_ANSWERS","prompt":"Which TWO benefits can all the members get?","options":[{"id":"practice-3-q4-a","label":"A","text":"free entry to some castles"},{"id":"practice-3-q4-b","label":"B","text":"subscription of Scottish magazines"},{"id":"practice-3-q4-c","label":"C","text":"discount of apartment rent"},{"id":"practice-3-q4-d","label":"D","text":"free visitor guide"},{"id":"practice-3-q4-e","label":"E","text":"free parking"}],"correctAnswers":[]}
            ]
          }]
        }]
      }
      $$::jsonb
    where not exists (select 1 from public.tests where title = 'Practice 3. A tour guide from Travel-lite Travel Agency');

    insert into public.tests(
      title, description, duration_minutes, status, created_by,
      primary_skill, test_type, version, tags, builder_content
    )
    select
      'Practice 4. Presentation on Foot Injuries among Sports Players',
      'User-provided Listening practice. Audio, transcript and answer key are pending.',
      10, 'DRAFT', actor_id, 'LISTENING', 'SINGLE_SKILL', 'v1.0',
      '["PRACTICE", "LISTENING", "SECTION_1", "PENDING_AUDIO", "PENDING_ANSWER_KEY"]'::jsonb,
      $$
      {
        "format": "SECTION_1",
        "sectionsPreset": "SECTION_1",
        "expectedQuestions": 4,
        "parts": [{
          "id": "practice-4-part-1",
          "partNo": 1,
          "title": "Presentation on Foot Injuries among Sports Players",
          "audioUrl": null,
          "audioFilename": null,
          "audioDurationSeconds": null,
          "transcriptHtml": "",
          "questionGroups": [{
            "id": "practice-4-group-1",
            "title": "Questions 27–28",
            "titleMode": "CUSTOM",
            "startQuestionNo": 1,
            "endQuestionNo": 2,
            "typeFormat": "MULTIPLE_ANSWERS",
            "requiredAnswerCount": 2,
            "instructions": "Choose TWO treatment techniques.",
            "answerSource": "PASSAGE",
            "questions": [
              {"id":"practice-4-q1","number":1,"typeFormat":"MULTIPLE_ANSWERS","prompt":"Which TWO treatment techniques did the female runner find useful for her swollen heel?","options":[{"id":"practice-4-q1-a","label":"A","text":"massage"},{"id":"practice-4-q1-b","label":"B","text":"ultrasound"},{"id":"practice-4-q1-c","label":"C","text":"rest"},{"id":"practice-4-q1-d","label":"D","text":"balancing exercises"},{"id":"practice-4-q1-e","label":"E","text":"ice"}],"correctAnswers":[]},
              {"id":"practice-4-q2","number":2,"typeFormat":"MULTIPLE_ANSWERS","prompt":"Which TWO treatment techniques did the female runner find useful for her swollen heel?","options":[{"id":"practice-4-q2-a","label":"A","text":"massage"},{"id":"practice-4-q2-b","label":"B","text":"ultrasound"},{"id":"practice-4-q2-c","label":"C","text":"rest"},{"id":"practice-4-q2-d","label":"D","text":"balancing exercises"},{"id":"practice-4-q2-e","label":"E","text":"ice"}],"correctAnswers":[]}
            ]
          }, {
            "id": "practice-4-group-2",
            "title": "Questions 29–30",
            "titleMode": "CUSTOM",
            "startQuestionNo": 3,
            "endQuestionNo": 4,
            "typeFormat": "MULTIPLE_ANSWERS",
            "requiredAnswerCount": 2,
            "instructions": "Choose TWO correct answers.",
            "answerSource": "PASSAGE",
            "questions": [
              {"id":"practice-4-q3","number":3,"typeFormat":"MULTIPLE_ANSWERS","prompt":"Which TWO sports did the male sprinter find most effective during his rehabilitation programme?","options":[{"id":"practice-4-q3-a","label":"A","text":"swimming"},{"id":"practice-4-q3-b","label":"B","text":"weight-training"},{"id":"practice-4-q3-c","label":"C","text":"running on grass"},{"id":"practice-4-q3-d","label":"D","text":"cycling"},{"id":"practice-4-q3-e","label":"E","text":"jumping"}],"correctAnswers":[]},
              {"id":"practice-4-q4","number":4,"typeFormat":"MULTIPLE_ANSWERS","prompt":"Which TWO sports did the male sprinter find most effective during his rehabilitation programme?","options":[{"id":"practice-4-q4-a","label":"A","text":"swimming"},{"id":"practice-4-q4-b","label":"B","text":"weight-training"},{"id":"practice-4-q4-c","label":"C","text":"running on grass"},{"id":"practice-4-q4-d","label":"D","text":"cycling"},{"id":"practice-4-q4-e","label":"E","text":"jumping"}],"correctAnswers":[]}
            ]
          }]
        }]
      }
      $$::jsonb
    where not exists (select 1 from public.tests where title = 'Practice 4. Presentation on Foot Injuries among Sports Players');
  end if;
end
$migration$;
