-- Session 2 map/diagram support: attach source images and use the frontend's
-- canonical DIAGRAM_LABELING type for map groups and questions.
do $migration$
declare actor_id uuid;
begin
  select id into actor_id from public.profiles order by created_at, id limit 1;
  if actor_id is not null then
    insert into public.learning_resources
      (id,title,description,skill,category,resource_type,scope,external_url,teacher_only,status,created_by)
    values
      ('30000000-0000-0000-0000-000000000001','Session 2 Bibliostack map','Map illustration for the Session 2 Listening practice.','LISTENING','MEDIA','DOCUMENT','GLOBAL',null,false,'PUBLISHED',actor_id),
      ('30000000-0000-0000-0000-000000000002','Session 2 Sports Information map','Map illustration for the Session 2 Listening practice.','LISTENING','MEDIA','DOCUMENT','GLOBAL',null,false,'PUBLISHED',actor_id),
      ('30000000-0000-0000-0000-000000000003','Session 2 Forest Lake map','Map illustration for the Session 2 Listening practice.','LISTENING','MEDIA','DOCUMENT','GLOBAL',null,false,'PUBLISHED',actor_id),
      ('30000000-0000-0000-0000-000000000004','Session 2 Hollies Supermarket map','Map illustration for the Session 2 Listening practice.','LISTENING','MEDIA','DOCUMENT','GLOBAL',null,false,'PUBLISHED',actor_id),
      ('30000000-0000-0000-0000-000000000005','Session 2 Community Centre map','Map illustration for the Session 2 Listening practice.','LISTENING','MEDIA','DOCUMENT','GLOBAL',null,false,'PUBLISHED',actor_id)
    on conflict (id) do nothing;

    insert into public.learning_resource_files
      (id,resource_id,file_role,storage_provider,bucket_name,object_path,original_filename,mime_type,size_bytes,uploaded_by)
    values
      ('40000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001','SUPPORTING','LOCAL',null,'resources/30000000-0000-0000-0000-000000000001/40000000-0000-0000-0000-000000000001-session2_bibliostack_map.png','session2_bibliostack_map.png','image/png',291318,actor_id),
      ('40000000-0000-0000-0000-000000000002','30000000-0000-0000-0000-000000000002','SUPPORTING','LOCAL',null,'resources/30000000-0000-0000-0000-000000000002/40000000-0000-0000-0000-000000000002-session2_sports_information_map.png','session2_sports_information_map.png','image/png',179482,actor_id),
      ('40000000-0000-0000-0000-000000000003','30000000-0000-0000-0000-000000000003','SUPPORTING','LOCAL',null,'resources/30000000-0000-0000-0000-000000000003/40000000-0000-0000-0000-000000000003-session2_forest_lake_map.png','session2_forest_lake_map.png','image/png',914302,actor_id),
      ('40000000-0000-0000-0000-000000000004','30000000-0000-0000-0000-000000000004','SUPPORTING','LOCAL',null,'resources/30000000-0000-0000-0000-000000000004/40000000-0000-0000-0000-000000000004-session2_hollies_map.png','session2_hollies_map.png','image/png',272029,actor_id),
      ('40000000-0000-0000-0000-000000000005','30000000-0000-0000-0000-000000000005','SUPPORTING','LOCAL',null,'resources/30000000-0000-0000-0000-000000000005/40000000-0000-0000-0000-000000000005-session2_community_centre_map.png','session2_community_centre_map.png','image/png',23609,actor_id)
    on conflict (id) do nothing;
  end if;
end $migration$;

-- The map groups were initially seeded with an internal name. Normalize both
-- group and question formats so the management and learner UIs render them as
-- diagram/map labelling exercises.
update public.tests
set builder_content = replace(builder_content::text, '"MAP_LABELING"', '"DIAGRAM_LABELING"')::jsonb
where title in (
  '(session2)_listening_bibliostack',
  '(session2)_listening_sports_information',
  '(session2)_listening_forest_lake',
  '(session2)_listening_hollies_supermarket',
  '(session2)_listening_plan_community_centre',
  '(session2)_listening_information_day',
  '(session2)_listening_bankside',
  '(session2)_listening_linwood',
  '(session2)_listening_clifton_bird_park'
);

update public.tests
set builder_content = jsonb_set(
  builder_content,
  '{parts,0,questionGroups,0,illustration}',
  '{"fileUrl":"/api/v1/admin/library/files/40000000-0000-0000-0000-000000000001/content","filename":"session2_bibliostack_map.png","assetId":"40000000-0000-0000-0000-000000000001","altText":"Bibliostack visitor centre map"}'::jsonb,
  true
)
where title = '(session2)_listening_bibliostack';

update public.tests
set builder_content = jsonb_set(
  builder_content,
  '{parts,0,questionGroups,0,illustration}',
  '{"fileUrl":"/api/v1/admin/library/files/40000000-0000-0000-0000-000000000002/content","filename":"session2_sports_information_map.png","assetId":"40000000-0000-0000-0000-000000000002","altText":"Sports information centre map"}'::jsonb,
  true
)
where title = '(session2)_listening_sports_information';

update public.tests
set builder_content = jsonb_set(
  builder_content,
  '{parts,0,questionGroups,0,illustration}',
  '{"fileUrl":"/api/v1/admin/library/files/40000000-0000-0000-0000-000000000003/content","filename":"session2_forest_lake_map.png","assetId":"40000000-0000-0000-0000-000000000003","altText":"Forest Lake nature reserve map"}'::jsonb,
  true
)
where title = '(session2)_listening_forest_lake';

update public.tests
set builder_content = jsonb_set(
  builder_content,
  '{parts,0,questionGroups,0,illustration}',
  '{"fileUrl":"/api/v1/admin/library/files/40000000-0000-0000-0000-000000000004/content","filename":"session2_hollies_supermarket_map.png","assetId":"40000000-0000-0000-0000-000000000004","altText":"Hollies Supermarket map"}'::jsonb,
  true
)
where title = '(session2)_listening_hollies_supermarket';

update public.tests
set builder_content = jsonb_set(
  builder_content,
  '{parts,0,questionGroups,2,illustration}',
  '{"fileUrl":"/api/v1/admin/library/files/40000000-0000-0000-0000-000000000005/content","filename":"session2_community_centre_map.png","assetId":"40000000-0000-0000-0000-000000000005","altText":"Community centre plan"}'::jsonb,
  true
)
where title = '(session2)_listening_plan_community_centre';
