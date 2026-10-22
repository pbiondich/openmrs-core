# Assertion model changelog

Changes on the `assertion-model/*` branches of this fork. The plan lives in `docs/dev-plan.md` in
`pbiondich/openmrs-assertion-tools`.

## Phase 1 — Typed principals

- `users.user_type` (`HUMAN` | `DEVICE` | `SOFTWARE`, default `HUMAN`), mapped as `User.userType` / `org.openmrs.UserType`.
  Existing users become `HUMAN`; the Daemon user becomes `SOFTWARE`.
- `users.person_id` is nullable in the database and the mapping. `UserValidator` still requires a person for every
  user type until the `getPerson()` callers that assume a person are fixed, and `UserService` refuses to save a HUMAN
  user without a person even when validation is disabled. Rolling back the changeset fails while any user has no
  person.
