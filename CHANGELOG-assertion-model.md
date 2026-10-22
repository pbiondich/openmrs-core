# Assertion model changelog

Changes on the `assertion-model/*` branches of this fork. The plan lives in `docs/dev-plan.md` in
`pbiondich/openmrs-assertion-tools`.

## Phase 1 — Typed principals

- `users.user_type` (`HUMAN` | `DEVICE` | `SOFTWARE`, default `HUMAN`), mapped as `User.userType` / `org.openmrs.UserType`.
  Existing users become `HUMAN`; the Daemon user becomes `SOFTWARE`. A user's type is set when it is created and never
  changes: the column is not updatable and `UserService` refuses a save that changes it.
- `users.person_id` is nullable in the database and the mapping. A HUMAN user must have a person: `UserValidator`
  checks it, and `UserService` refuses to save one without a person even when validation is disabled. DEVICE and
  SOFTWARE users may have none. Rolling back the changeset fails while any user has no person.
- User search (`getUsers`, `getCountOfUsers`) finds users without a person by username or system id, and
  `UserByNameComparator` sorts them after named users, by username.
