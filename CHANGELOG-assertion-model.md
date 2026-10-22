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
- `software_agent` (`user_id` PK/FK users, `name`, `version`, `model_identifier`, `config_hash`; `(name, version)`
  unique), mapped as `org.openmrs.SoftwareAgent`. Decision 1 is settled: the version is part of the identity, so each
  version is its own SOFTWARE user and its own row, and all versions of the software share its name (the plan's
  `family_id` was dropped: the name already identifies the software). A changed model or configuration needs a new
  version; `model_identifier` and `config_hash` describe a version but do not identify it. Rows are never changed
  (`@Immutable`).
- `UserService.createSoftwareAgent(agent, password)` creates the SOFTWARE user and its agent together, so every
  software user created this way has one; `getSoftwareAgent(User)` and `getSoftwareAgentVersions(name)` read them.
  `User` has no link to its software agent, so loading a user never loads one.
- `device_agent` (`user_id` PK/FK users, `identifier` unique, `location_id` FK location), mapped as
  `org.openmrs.DeviceAgent`. `UserService.createDeviceAgent(agent, password)` creates the DEVICE user and its agent
  together. The identifier is the implementation's own identifier for the device, the one its messages carry; it never
  changes (no setter, not updatable). The location is where the device is now: `moveDeviceAgent(device, location)`
  changes only that, authorized by the device's user id. Devices are not versioned: new firmware is not a new device.
  `getDeviceAgent(User)` and `getDeviceAgentByIdentifier(identifier)` read them.
- `provider.user_id` (nullable, unique, FK users) and `provider.provider_type` (`BIOLOGIC` | `DIGITAL`, default
  `BIOLOGIC`), mapped as `Provider.user` / `Provider.providerType` / `org.openmrs.ProviderType`. A DIGITAL provider is
  backed by one software or device user, never the Daemon, and no person; a BIOLOGIC provider has no user. The
  validator and `saveProvider` both enforce this, since validation can be disabled. Binding a user to a new digital
  provider also needs Edit Users. A provider's type and user never change (the columns are not updatable, and
  `saveProvider` and `retireProvider` refuse a change), so its past assertions stay its own. A digital provider's name
  is its user's username, and provider search matches the user's username and system id.
  `ProviderService.getProviderByUser(User)` finds it.
- `obs.asserter_id` (nullable, indexed, FK provider), mapped as `Obs.asserter`: the provider who made the claim an obs
  records; null means unknown. `ObsService.saveObs` fills it on a new obs when the caller leaves it unset: a group
  member takes its parent's; otherwise a software or device creator's digital provider, or a person's provider when
  the person has exactly one that is not retired; anything else stays unknown. An edit keeps the claim's asserter
  unless the caller sets another, so moving an encounter or merging patients never re-attributes obs. Existing obs are
  backfilled only where their encounter has exactly one provider. `ObsService.getObservationsByAsserter` and
  `getObservationsBySoftwareVersion` find them, a page at a time.
