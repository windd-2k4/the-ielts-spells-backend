# JUNE cohort demo seed

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
