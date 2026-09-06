## Goal

Implement tree-ware `set` (write) and `get` (read) operators for Elasticsearch in `tree-ware-kotlin-elasticsearch`, mirroring the two-layer structure of `tree-ware-kotlin-mysql`: pure request/statement generation plus a thin execution layer that issues them to the datastore.
It is ok to update the existing `tree-ware-kotlin-elasticsearch` code as needed.

## Success Criteria

- A caller can create indices, write a tree-ware tree to Elasticsearch, and read it back via a search string, using only the new ES operators.
- Every stored document carries an `entity_path_` field (similar to the MySQL `field_path_`); `get` reconstructs the response tree by combining paths from hits across all indices.
- Each implementation step below is independently committable in `tree-ware-kotlin-elasticsearch` with its own passing unit tests, per `AGENTS.md`.

## Context And Current Facts

- `tree-ware-kotlin-elasticsearch/src/main/.../elasticsearch/index/CreateIndexRequests.kt` already generates one `CreateIndexRequest` per meta-model entity (index name `<package>__<entity>`, field-type-to-mapping conversion). This is the DDL-generation analog and is covered by `CreateIndexRequestsTests.kt` golden JSONs in `src/test/resources/elasticsearch/mappings/` with `JsonTestUtils` serialization helpers. What is missing is (a) issuing index creation to a live client, (b) document write generation + execution, (c) search/get.
- MySQL reference structure (`tree-ware-kotlin-mysql/src/main/.../mySql/operator/`):
  - Pure generation: `GenerateDdlCommands.kt` (+ `ddl/`, `operator/ddl/`), `delegate/SetCommandBuilder.kt`, `delegate/GetCommandBuilder.kt`, `delegate/SqlColumn.kt`, tested without a DB by `GenerateSetCommandsTests.kt`, `GenerateChangeLogTests.kt`.
  - Execution: `Set.kt` / `Get.kt` open a `DataSource.connection` and delegate to `MySqlSetDelegate` / `MySqlGetDelegate`, with `logCommands` flag, commit/rollback, and per-entity error lists. `CreateDatabase.kt` issues DDL.
  - Extensibility: `RegisterMySqlOperatorDelegates.kt` registers per-entity delegates (e.g. geo point) for DDL/set/get; `delegate/geoPoint/` implements them.
- ES module pins `co.elastic.clients:elasticsearch-java:9.0.3` in `tree-ware-kotlin-elasticsearch/build.gradle.kts`; `CreateIndexRequests.kt` imports from that client. Tests use JUnit5 with `integrationTests` system-property tagging (`include`/`exclude`).
- `tree-ware-kotlin-mysql` and `tree-ware-kotlin-elasticsearch` are separate repos (nested git); commits for this work go in the ES repo (`git -C tree-ware-kotlin-elasticsearch ...`) with `<AI>`-prefixed messages per `AGENTS.md`.
- `tree-ware-kotlin-proto3` is a guide only for the pure-visitor/codec pattern (`aux/*MetaModelMap*`, `message/EncodeProto3.kt`, golden-style `Proto3GenerationTests.kt`); no proto runtime dependency is needed in ES.

## Constraints And Non-goals

- Scope is `tree-ware-kotlin-elasticsearch` main + test source only. No changes to `tree-ware-kotlin-mysql`, `tree-ware-kotlin-proto3`, core, server, or e2e-shell (e2e wiring, if wanted, is a follow-up).
- `get` input is a plain search string run against all indices (per the request); no MySQL-style request-tree/field-projection semantics.
- No Liquibase/changelog analog (MySQL-only concern). Non-goals: RBAC, auth, pagination UI, index lifecycle management, reindex/migration tooling.

## Key Decisions

1. **Reuse `CreateIndexRequests` as the DDL generator; add an execution function.** It already encodes the `<package>__<entity>` naming and field mappings. Add `createIndices(client, metaModel)` mirroring MySQL `CreateDatabase.kt`, plus an `entity_path_` field in each mapping (needed for get). Alternative (a separate DDL model) rejected: duplicates tested behavior.
2. **Mirror the two-layer API shape.** Pure `generate*Requests(model) -> List<...>` functions (unit-testable, golden-friendly) plus thin `set(model, ..., client)` / `get(searchString, ..., client, responseModel)` executors mirroring `Set.kt`/`Get.kt` signatures (with `logRequests: Boolean = false` instead of `logCommands`). Keeps MySQL familiarity and testability.
3. **One document per entity instance; `_id` = `entity_path_`, `entity_path_` also stored in `_source`.** Gives idempotent writes and trivial subtree deletes, and makes get's path-combining direct. Alternative (auto-generated `_id` + entity_path_ only in source) rejected: complicates overwrite/delete and dedup.
4. **Write path: flatten via a set-visitor; issue with Bulk API.** The visitor emits per-entity upsert/delete operations (create/update/delete determined the same way `MySqlSetDelegate` uses `SetAux`); the executor sends them as one `_bulk` request per `set()` call with per-item error reporting (`Response.ErrorList`, no partial-commit analog needed — report item errors like `issueCommands()` does). Alternative (one index request per entity) rejected: N round-trips.
5. **Read path: `query_string` search across all indices, group hits by `entity_path_`.** No per-field SELECT/projection layer; decode each hit's source fields into the response model by path, opposite of the write flattener. Custom-field decoding goes through entity delegates (decision 6).
6. **Keep the delegate-registry extension pattern.** Add `RegisterElasticsearchOperatorDelegates.kt` mirroring `RegisterMySqlOperatorDelegates.kt`; ship geo-point support first (ES has a native `geo_point` type — mapping + ser/deser delegate), since MySQL already singles it out and the test meta-model contains `org.tree_ware.meta_model.geo__point`.

## Recommended Approach

Follow the MySQL layering exactly: pure generation first (goldens, no client), then executors (stub/fake-client unit tests), then delegates, then live integration tests. Add the `entity_path_` field to mappings up front (Step 1 touches goldens deliberately so later steps never invalidate earlier tests). Keep public function signatures parallel to `Set.kt`/`Get.kt` so server/e2e code can adopt them later without relearning.
Follow the same sub-package structure as `tree-ware-kotlin-mysql` as far as possible.

## Work Plan

Each step is one independent commit in `tree-ware-kotlin-elasticsearch` (commit per `AGENTS.md`: `<AI>` prefix + prompt/plan/model/time).

- **Step 0 — Update index mappings: add `entity_path_` field + index-creation executor, drop mapping for compositions (since each entity has a dedicated index), check if any of the existing mappings can be improved (the goal is to be able to search for any text in any field).**
  Surfaces: `index/CreateIndexRequests.kt`.
  Tests: update `CreateIndexRequestsTests` accordingly (assert `entity_path_` in every mapping).
  Depends on: nothing.

- **Step 1 — Index-creation executor.**
  Surfaces: new `index/CreateIndices.kt` (`fun createIndices(client: ElasticsearchClient, metaModel, ...)`), goldens in `src/test/resources/elasticsearch/mappings/`.
  Tests: new `CreateIndicesTests` with a fake `ElasticsearchClient` verifying one create call per entity.
  Depends on: Step 0

- **Step 2 — Pure write-request generation (ES analog of DML generation).**
  Surfaces: new `operator/GenerateDocumentRequests.kt` — a model visitor flattening a tree into `List<DocumentOperation>` (`Create(entity_path_, index, source-map)` / `Update(entity_path_, index, partial-source-map)` / `Delete(entity_path_, index)`), embedding `entity_path_` in every source; index name resolution reused from Step 1.
  Tests: new `GenerateDocumentRequestsTests` with golden JSON per entity (address-book fixtures, mirroring `GenerateSetCommandsTests.kt` + `GenerateMappingsGoldens.kt` pattern); covers create, update, delete, keyless/singleton entities.
  Depends on: Step 1. No client code.

- **Step 3 — `set` executor (issue writes via Bulk API).**
  Surfaces: new `operator/Set.kt` (`fun set(model, entityDelegates?, client: ElasticsearchClient, logRequests = false): Response`) + internal bulk-issuing delegate mirroring `MySqlSetDelegate.issueCommands()` ordering (deletes before creates/updates) and error mapping to `Response.ErrorList`.
  Tests: `SetTests` against a fake/stubbed bulk endpoint (success, item-failure, transport-exception paths; assert `logRequests` output).
  Depends on: Step 2. Commits independently (Step 2 stays green without it).

- **Step 4 — `get` operator (search string → path-combined response).**
  Surfaces: new `operator/Get.kt` (`fun get(searchString, ..., client, responseModel, logRequests = false): Response`): `search(q = searchString, index = "_all")`, group hits by `entity_path_` source field, decode remaining source fields into the response tree.
  Tests: `GetTests` with canned search hits (multi-index hits, empty result, missing-`entity_path_` hit, decode error); assert combined tree equals expected (the "simpler than MySQL" contract).
  Depends on: nothing at runtime (reads whatever is indexed); logically after Step 1. Independently committable and testable with fixtures.

- **Step 5 — Entity delegates + geo-point support.**
  Surfaces: new `operator/delegate/RegisterElasticsearchOperatorDelegates.kt` + `delegate/geoPoint/` (mapping override to `geo_point`, document ser/deser), generic `Get/SetEntityDelegate` hooks mirroring MySQL's.
  Tests: mapping test (point entity → `geo_point`), round-trip doc test, registry test.
  Depends on: Steps 1–2 (hooks they plug into); commits independently.

- **Step 6 — Live integration tests + README.**
  Surfaces: `src/test/.../IntegrationTests.kt` tagged `integrationTest` (spins up Elasticsearch, runs create-indices → set → get round-trip on the address-book model), plus a short README section documenting `set`/`get` usage.
  Validation: `./gradlew test -DintegrationTests=include` locally/CI; default `./gradlew test` still excludes them.
  Depends on: Steps 1, 3, 4. Docs-only + tagged tests; safe to land last.

## Validation Plan

- Per step: `cd tree-ware-kotlin-elasticsearch && ./gradlew test` (default excludes `integrationTest` tag); Step 6 additionally runs with `-DintegrationTests=include` against a local Elasticsearch 9.x.
- Highest-risk check: Step 4's path-combining test with multi-index canned hits — it pins the core simplified-get contract; if it passes and Step 2's goldens pass, the round-trip (Step 6) should follow.
- After each step: `git -C tree-ware-kotlin-elasticsearch status --short` shows only intended files; commit message follows `AGENTS.md` (`<AI>` prefix + prompt/plan/model/time).

## Risks / Rollback

- **Mapping gaps** (composition `nested` vs flat, association encoding, password/blob handling): `CreateIndexRequests.kt` currently maps compositions to `nested` and several types to `keyword` — Step 2 may force mapping revisions; contained by golden tests (rollback = revert that step's commit).
- **`_id` = path collisions/escaping** for deep/keyless paths: watch Step 6 round-trip; fallback is auto `_id` + path-only lookup (would change Step 2/3 only).
- **Search-across-all-indices relevance/noise**: `query_string` over `_all` may match mapping noise; mitigate with `lenient` + documenting query syntax; no schema change needed.
- Each step is a separate commit, so rollback is `git -C tree-ware-kotlin-elasticsearch revert <step-commit>` without affecting earlier steps.

## Open Questions

- Bulk sizing/retry policy for very large trees (default: single bulk call, no retry; acceptable for v1, tunable later).
- Whether subtree delete should delete by `_id` list from the model or by `entity_path_` prefix query — recommended: `_id` list from the flattened model in Step 2 (exact, no query-side effects).
