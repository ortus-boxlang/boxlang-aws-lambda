# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

* * *

## [Unreleased]

### Added

- `BOXLANG_ENABLE_ROOT_SCAN` (shared across all serverless runtimes, default `true`): set to `false` to opt out of the legacy root-directory scan used when neither `manifest.json` nor `handlers/` is present, restricting routing to the default handler only.
- `BOXLANG_RESPONSE_MODE`: `http` (default, unchanged envelope) or `raw` (nothing predefined, only `response.body` is returned, unwrapped, so direct invocations and REST API non-proxy integrations get exactly what the code produced). An invalid value aborts cold start.

### Changed

- The `response` struct is now passed as the last argument to the `Application.bx` `onRequestEnd` and `onError` hooks, and the handler's return value is assigned to `response.body` before `onRequestEnd`, so hooks can wrap or replace the body and set the status. Hooks that do not declare the extra argument are unaffected.
- A handled error now defaults the response status to `500` unless `onError` sets one (it was `200`).
- A present-but-corrupt `manifest.json` now restricts routing to the default handler only, instead of falling back to a `handlers/` or root-directory scan.
- `handleRequest` now returns `Object` instead of `Map<?, ?>` so raw mode can return any JSON value. In `http` mode it is still the response struct. Java callers that used `var` and called `Map` methods on the result need a cast.

### Fixed

- `Application.bx` is now loaded for requests routed to a class under `handlers/`, so `onRequestStart`, datasources and every other `Application.bx` setting apply to routed handlers (previously only the default handler saw them).

### Security

- Convention-based URI routing and the `x-bx-function` header are by design, but they also let an unauthenticated request reach `Application.bx`'s lifecycle callbacks (and any other root-level `.bx` file). Routing is now scoped to a `handlers/` directory convention (or a build-time `manifest.json` allowlist); `Application.bx` and the default `Lambda.bx` are never eligible routing targets, even under the legacy backward-compatibility fallback for existing deployments.
- `manifest.json` `reserved` and `defaultHandler` are now enforced, not just documented: reserved files can never be routed, `defaultHandler.file` and `method` are honored, and handler files that do not exist are skipped.
- `manifest.json` handler and `defaultHandler` paths are normalized and confined to the deployment root, so `../` can no longer route to files outside it.
- Setting `defaultHandler.file` to `Application.bx` now aborts cold start with a clear error instead of crashing with `duplicate element` and leaving the reserved file in effect as the default handler.

## [1.17.6] - 2026-09-26

## [1.17.5] - 2026-09-18

## [1.17.0] - 2026-08-28

## [1.16.0] - 2026-07-30

## [1.15.0] - 2026-07-08

## [1.14.0] - 2026-06-03

## [1.13.0] - 2026-05-01

### Added

- new `KeyDictionary` to track common header keys

### Changes

- Updated the way request context is built as per 1.12 release, to ensure that the correct lambda path is used when URI routing is enabled.

## [1.12.0] - 2026-04-08

## [1.11.0] - 2026-03-04

## [1.10.1] - 2026-02-04

## [1.10.0] - 2026-02-02

## [1.9.0] - 2026-01-08

## [1.8.0] - 2025-12-05

## [1.7.0] - 2025-11-04

## [1.6.1] - 2025-10-07

### Fixed

- Relocated `org.objectweb.asm` package to avoid conflicts with AWS Lambda environment.

## [1.6.0] - 2025-10-03

- <https://boxlang.ortusbooks.com/readme/release-history/1.6.0>

## [1.5.1] - 2025-08-30

### Fixed

- Fixed issue with shadowJar task not including all necessary files only in AWS Runtime

## [1.5.0] - 2025-08-30

- <https://boxlang.ortusbooks.com/readme/release-history/1.5.0>

## [1.4.0] - 2025-08-02

- <https://boxlang.ortusbooks.com/readme/release-history/1.4.0>

## [1.3.0] - 2025-06-23

- <https://boxlang.ortusbooks.com/readme/release-history/1.3.0>

## [1.2.0] - 2025-05-29

- <https://boxlang.ortusbooks.com/readme/release-history/1.2.0>

## [1.1.0] - 2025-05-12

- <https://boxlang.ortusbooks.com/readme/release-history/1.1.0>

## [1.0.1] - 2025-05-01

- <https://boxlang.ortusbooks.com/readme/release-history/1.0.1>

## [1.0.0] - 2025-04-30

- <https://boxlang.ortusbooks.com/readme/release-history/1.0.0>

* * *

[unreleased]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.17.6...HEAD
[1.17.6]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.17.5...v1.17.6
[1.17.5]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.17.0...v1.17.5
[1.17.0]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.16.0...v1.17.0
[1.16.0]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.15.0...v1.16.0
[1.15.0]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.14.0...v1.15.0
[1.14.0]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.13.0...v1.14.0
[1.13.0]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.12.0...v1.13.0
[1.12.0]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.11.0...v1.12.0
[1.11.0]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.10.1...v1.11.0
[1.10.1]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.10.0...v1.10.1
[1.10.0]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.9.0...v1.10.0
[1.9.0]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.8.0...v1.9.0
[1.8.0]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.7.0...v1.8.0
[1.7.0]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.6.1...v1.7.0
[1.6.1]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.6.0...v1.6.1
[1.6.0]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.5.1...v1.6.0
[1.5.1]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.5.0...v1.5.1
[1.5.0]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/ortus-boxlang/boxlang-aws-lambda/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/ortus-boxlang/boxlang-miniserver/compare/e31fe4ded229e36b940fea08bef9239588599479...v1.0.0
