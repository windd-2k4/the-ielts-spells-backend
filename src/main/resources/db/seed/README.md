# Database seed scripts

## Supabase demo seed (current schema)

`seed_supabase_demo.sql` is the recommended, small idempotent seed for a
Supabase project that has already applied the backend Flyway migrations.
It creates a representative operations dataset: two courses, four sessions,
two learning resources, one exercise, one Reading-builder draft, and two leads.

It uses an existing active application administrator from `public.profiles`
and `public.user_roles` (`role = 'ADMIN'`). It deliberately does **not** create
or modify `auth.users`, profiles, roles, staff records, student records,
enrollments, or attendance.

Open `seed_supabase_demo.sql` in **Supabase Dashboard → SQL Editor** and run it
as the database-admin role. The script is safe to re-run because it updates the
same records through stable codes and IDs. Sessions have no teacher account;
leads are assigned to the existing administrator.

Because it has no student Auth users, it intentionally does not create
enrollments or attendance. Create real student accounts and enroll them through
the management portal when that data is needed.

You can confirm that the existing administrator is available before running:

```sql
select p.id, p.email, r.role
from public.profiles p
join public.user_roles r on r.user_id = p.id
where p.is_active = true
  and r.role = 'ADMIN';
```

## Legacy JUNE cohort seed

`seed_june_cohorts.sql` is an idempotent development/demo seed generated from:

- `THEO DÕI TIẾN ĐỘ - JUNE 1.xlsx`
- `Điểm Danh - JUNE 1.xlsx`
- `Theo dõi tiến độ - JUNE 2.xlsx`
- `Điểm Danh - JUNE 2.xlsx`

It creates one shared Listening/Reading Foundation course, two classes, 48 cohort-scoped student records, enrollments, 25 completed class sessions, 600 attendance records, and starter curriculum modules derived from the progress workbook sheet groups.

The workbooks contain student names but no verified email addresses or phone numbers. The seed therefore uses non-deliverable `@seed.theieltsspells.local` aliases and leaves phone numbers empty. Duplicate names across cohorts remain separate records because the source has no stable identifier that proves they are the same person.

Run only in a local or disposable demo database after Flyway migrations:

```powershell
psql $env:DATABASE_URL -v ON_ERROR_STOP=1 -f src/main/resources/db/seed/seed_june_cohorts.sql
```

Do not execute this file against production Supabase Auth. Real student accounts must be created through the approved account/invitation workflow.
