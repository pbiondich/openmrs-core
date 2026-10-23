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
- Privileges `Create Preliminary Obs` and `Attest Obs` (`PrivilegeConstants.CREATE_PRELIMINARY_OBS` / `ATTEST_OBS`),
  created at startup like other core privileges. Phase 2 enforces them together with the finalization policy, since
  devices auto-finalize.
- The Daemon user no longer has a person. It shared person 1 with the admin user only because `users.person_id` used
  to be required; rolling back gives it person 1 again.
- `Obs.Status.ENTERED_IN_ERROR`: `ObsService.voidObs` marks the obs, and the members of a group voided with it, as
  entered in error, unless a newer version replaced it (that void is how an edit is saved, and the old version keeps
  its status). Unvoiding it, through `unvoidObs` or by saving it with `voided` cleared, gives it the status a new obs
  would get on the same row; the status it had before is not kept. Entering or leaving the status does not make an obs dirty, and
  the obs interceptor allows only that one change on an obs that is not voided. No schema change: `obs.status` is
  already `varchar(16)`. Obs voided before this change are not backfilled, since their status would be lost.
- `obs_finalization_policy` (`ObsFinalizationPolicy`, metadata): for an agent type (`HUMAN` | `SOFTWARE` | `DEVICE`),
  optionally narrowed to a concept class, the action `AUTO_FINAL` or `REQUIRE_ATTESTATION`. Seeded with DEVICE ->
  `AUTO_FINAL` and SOFTWARE -> `REQUIRE_ATTESTATION`; the concept class's policy takes precedence over the agent
  type's. `ObsService.saveObs` saves a new obs `PRELIMINARY`, whatever status the caller gave it, when the policy for
  the authenticated user's type or for the asserter's type requires attestation. The user making the call always
  counts, since a caller can name any asserter or creator; the Daemon is no agent, so obs it saves (HL7, scheduled
  tasks) are untouched. An edit is preliminary when the editor's policy requires attestation, or when the obs was
  still preliminary and its asserter's policy does, so a person amending an attested obs gets `AMENDED`. Unvoiding an
  obs that was entered in error applies the same rule, and a voided obs's status cannot be changed. `min_score` from
  the plan waits for Phase 3, when there is a score to compare. Privileges `Get Obs Finalization Policies` and
  `Manage Obs Finalization Policies`.
- `obs_attestation` (`ObsAttestation`: encounter, attester, attested_at) and `obs_attestation_item`
  (`ObsAttestationItem`: obs, outcome `ACCEPTED` | `REJECTED` | `AMENDED` | `LEFT_PRELIMINARY`, superseding obs).
  `AttestationService.attest(encounter, attester, items)` needs `Attest Obs`, now enforced, and an attester who is a
  person's provider, not retired, and the authenticated user's own. An accepted obs, with the preliminary members of
  its group, becomes `FINAL` on the same row through one guarded update, so it is not an edit; an amended obs is
  replaced by the item's correction, saved `FINAL` with the attester as asserter, and voided as replaced; a rejected
  obs is invalid, so it is voided as entered in error with the members of its group, and its item records who rejected
  it (decided with Paul: rejected and invalidated are the same here, and nothing else is hidden from reads). Each obs is judged as it is
  saved, all about one patient, and a group member with its own item follows that item. `getPendingForPatient` lists
  the preliminary obs that are not voided; `getObsAttestationItems` says
  what attestations made of an obs.
- `dictionary_version` (`DictionaryVersion`, metadata: a unique label, CIEL, SNOMED and LOINC releases, and
  `in_use_from`) and `encounter.dictionary_version_id`. The current version is the newest one, not retired, whose
  `in_use_from` has passed; `EncounterService.saveEncounter` records it on each encounter entered now, unless the
  caller set one, and the stamp is never changed after. A copy made by `transferEncounter` keeps the original's
  version, even an unknown one. Existing encounters are not backfilled: null means unknown, which an "unknown" row
  would only restate while making every encounter row change. `ConceptService` gets versions by uuid, by label and the
  current one, and saves and retires them under the concept privileges.
- `capture` (`Capture`, data: encounter, kind `AUDIO` | `VIDEO` | `HANDWRITTEN` | `SCANNED_FORM` | `TYPED_TEXT` |
  `IMAGE`, obs_id, derived_from_capture_id, mime_type, sha256, consent_status `NOT_RECORDED` | `GIVEN` | `WITHDRAWN`,
  expires_at, artifact_deleted_at). Every capture sits on a complex obs of its encounter, which holds the artifact as
  received and is never overwritten (decided with Paul). `CaptureService.createCapture` takes a complex concept whose
  handler stores bytes as received (not the image or text handler, which re-encode); it reads, counts and checksums
  the upload before storage sees it (storage copies on another thread and takes a stall for the end), checks the
  stored length, saves the obs final whatever the finalization policy (the artifact is evidence, not a claim) and
  titled with the capture's uuid, which must be a uuid, and deletes the stored file if the transaction rolls back.
  An intermediate form, such as a transcript, is a capture derived from another of the same encounter. Each kind
  allows only its own media types (audio, video, images, PDF, plain text; never HTML or SVG), and artifacts are capped
  at `capture.max_size_mb` (default 100). Audio and video expire after `capture.retention_days` (default 90); every
  other kind is kept, and a null expires_at means kept. Once saved, the obs, checksum, expiry, consent and deletion
  columns are read-only in the mapping; the service changes consent and deletion with bulk updates, going by what is
  in the database (Envers does not see those), and `voidCapture` refuses an unsaved capture. A daily
  `CaptureRetentionTaskHandler` deletes expired artifacts, each in its own transaction: the file is deleted, every
  live version of the complex obs voided through the session with its status kept (it was not entered in error), and
  the capture marked; a delete only counts once storage no longer has the file. Withdrawing consent deletes an
  expiring artifact at once; scanned forms and other kept artifacts are never deleted, the row always stays, and
  voided captures, or an artifact whose obs was voided other than by an edit, are not served. `ObsService` no longer
  deletes a complex obs's file on edit when the new version kept it or a capture holds it, judged on the obs as saved;
  purging an obs deletes the captures holding its file unless something points to them, and is refused if so; and a
  voided complex obs whose file is gone is fetched without its data rather than failing (any other failure still
  fails). Privileges `Get Captures`, `Add Captures` and `Edit Captures`; creating a capture also needs Add
  Observations.
- `derivation` (`Derivation`, data: capture, agent_user_id, dictionary_version_id, started, finished, params_hash):
  one finished run of an agent, such as an extraction model, over a capture. `CaptureService.createDerivation` defaults
  the agent to the authenticated user and the dictionary version to the current one, takes another user as agent only
  if it is software or a device (never another person), needs the run to have finished
  no earlier than it started, and refuses a voided capture; every column is read-only once saved. Privilege
  `Add Derivations`; reading needs `Get Captures`.
- `obs.capture_id`, `obs.span` (VARCHAR(255)) and `obs.derivation_id`: where an obs was taken from. The span is a
  varchar grammar: a locator `t=<seconds>-<seconds>` (audio, video), `xywh=<x>,<y>,<w>,<h>` (pages and images) or
  `c=<from>-<to>` (typed text), and/or `;`-separated `name=value` parameters such as `field=chest_indrawing`.
  Region units are image pixels, or points on a PDF page; `c=` counts Unicode code points; `page=` is reserved for
  the page of a multi-page capture; values are letters, digits and `_ . : -` only. `ObsValidator` checks that a span
  has a capture, follows the grammar and fits the capture's kind, that the capture is of the obs's own encounter and
  patient, that a derivation is over the same capture, and that a new obs points to no voided capture or derivation.
  An edited obs keeps all three; `transferEncounter`'s copies drop them, since the capture stays with the encounter it
  was captured in, and the voided originals keep them. Adding the two foreign keys rebuilds `obs` on MySQL, as the
  asserter changeset already does.
