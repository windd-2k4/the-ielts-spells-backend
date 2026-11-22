-- Normalize the source document's replacement-character apostrophe into a stable title.
update public.tests
set title='(session1)_reading_a_survivors_story'
where title like 'Practice 6.%'
  and title not like '(session1)%';
