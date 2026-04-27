# Compatibility Matrix

This document describes what `netconf-java` currently implements. It is an implementation matrix, not a blanket compliance claim. Interoperability still depends on the server's advertised capabilities and on whether the application stays within the library's supported session model.

Mental model: today the library is a synchronous, JSch-backed NETCONF-over-SSH client. Its strongest path is one sequential RPC conversation per `NetconfSession`, with explicit timeouts and explicit cleanup. Core NETCONF 1.0 and 1.1 framing is supported, several Junos workflows are wrapped ergonomically, and optional features are beginning to be enforced through capability negotiation, though coverage is not yet uniform across all extensions.

## Status legend

| Status | Meaning |
| --- | --- |
| `Supported` | Implemented and intended for normal use. |
| `Supported with caveats` | Implemented, but there are negotiation, API-surface, or interoperability caveats. |
| `Partial` | Some pieces exist, but coverage is incomplete or not first-class. |
| `Not implemented` | No dedicated support today. |

## Core NETCONF and transport

| Standard / feature | Status | Notes |
| --- | --- | --- |
| RFC 6242 SSH subsystem transport | `Supported` | `Device` opens an SSH subsystem channel with `subsystem=netconf` over JSch. |
| RFC 6241 `<hello>` parsing and generation | `Supported with caveats` | `Hello` parsing/building works, `Hello.builder()` auto-adds `urn:ietf:params:netconf:base:1.1`, and session establishment now fails if the peers do not share a common NETCONF base capability. The remaining caveat is that the client still cannot intentionally advertise only `base:1.0`. |
| NETCONF 1.0 end-of-message framing (`]]>]]>`) | `Supported` | Legacy framing is still supported for both outbound and inbound messages. |
| NETCONF 1.1 chunked framing | `Supported` | Chunked framing is selected when both peers share `urn:ietf:params:netconf:base:1.1`. |
| NETCONF 1.0 server interoperability | `Supported with caveats` | A 1.0-only server can interoperate because the client still advertises `base:1.0` and can read/write legacy framing. |
| NETCONF 1.0-only client advertisement | `Not implemented` | The client cannot intentionally advertise only `base:1.0`; `Hello.builder()` always injects `base:1.1`. |
| RPC `message-id` generation and reply correlation | `Supported with caveats` | Missing `message-id` attributes are injected, replies are validated, and sequential same-session alignment is covered by tests. One `NetconfSession` is still a sequential conversation, not a safe multiplexed channel for concurrent in-flight RPCs. |
| `<rpc-error>` parsing | `Supported` | `RpcReply` parses `error-type`, `error-tag`, `error-severity`, `error-path`, `error-message`, and common `error-info` fields into structured objects. |
| `<close-session>` | `Supported` | `NetconfSession.close()` sends `<close-session/>` and disconnects the channel. |
| `<kill-session>` | `Supported` | `NetconfSession.killSession(String)` is implemented. |
| Secure XML parsing | `Supported` | DTDs and XXE resolution are disabled for `Device`, `Hello`, and `RpcReply` parsing paths. |

## Standard capabilities and common operations

| Capability / operation | Status | Notes |
| --- | --- | --- |
| `<get>` | `Supported` | `getRunningConfigAndState(...)` issues `<get>`. |
| `<get-config>` | `Supported` | Candidate and running helpers exist. |
| `<edit-config>` to candidate | `Supported with caveats` | `loadXMLConfiguration(...)` and `loadTextConfiguration(...)` target `candidate`, and candidate-dependent operations now fail locally when the server did not advertise candidate support. The API still does not expose `test-option` or `error-option`. |
| `:candidate:1.0` | `Supported with caveats` | Candidate-oriented helpers are a primary workflow and are now runtime-gated against the server `<hello>`, but the default client capability still uses the legacy `urn:ietf:params:netconf:base:1.0#candidate` form rather than the RFC 6241 `urn:ietf:params:netconf:capability:candidate:1.0` URN. |
| `<commit>` | `Supported` | Standard commit is implemented. |
| `:confirmed-commit:1.1` | `Supported with caveats` | `commitConfirm(seconds, persistToken)` and `cancelCommit(persistId)` are runtime-gated. Persist-based flows require modern `confirmed-commit:1.1`, while legacy confirmed-commit remains usable for same-session confirmation flows without `persist`. The default client capability advertisement still uses the legacy `base:1.0#confirmed-commit` form. |
| `:validate:1.0` | `Supported with caveats` | `validate()` is implemented against candidate and now fails locally when validate support is absent, but default advertisement still uses the legacy `base:1.0#validate` URI. |
| `<lock>` / `<unlock>` | `Partial` | Candidate lock/unlock helpers exist. There is no first-class running datastore lock helper. |
| `:writable-running:1.0` | `Partial` | Running config retrieval exists, but there is no `edit-config` helper that targets `running` and the capability is not advertised by default. |
| `:startup:1.0` | `Not implemented` | No first-class startup datastore copy/delete flows are present. |
| `:url:1.0` | `Partial` | The default client capability list advertises the legacy `base:1.0#url?protocol=http,ftp,file` form, but there is no dedicated URL-based copy/load API surface. |
| `:xpath:1.0` | `Partial` | The library can pass caller-supplied filter XML through to `<get>` and `<get-data>`, but there is no typed XPath filter API and no runtime capability check. |
| `:rollback-on-error:1.0` | `Not implemented` | No API surface for `error-option rollback-on-error`. |
| `<discard-changes>` | `Not implemented` | No dedicated helper today. |
| `<copy-config>` | `Not implemented` | No dedicated helper today. |
| `<delete-config>` | `Not implemented` | No dedicated helper today. |
| RFC 6243 `with-defaults` | `Not implemented` | No explicit support or helper surface. |

## NMDA and additional datastore support

| Standard / feature | Status | Notes |
| --- | --- | --- |
| RFC 8526 `<get-data>` / `ietf-netconf-nmda` | `Supported with caveats` | `getData(xpathFilter, datastore)` emits NMDA namespace-qualified requests and can target `running`, `candidate`, `startup`, `intended`, or `operational`. The call is not capability-gated; the application must know the server supports NMDA. |
| RFC 8342 datastore naming | `Partial` | `Datastore` includes `RUNNING`, `CANDIDATE`, `STARTUP`, `INTENDED`, and `OPERATIONAL`, but only `getData(...)` uses that model directly. |

## Notifications and subscriptions

| Standard / feature | Status | Notes |
| --- | --- | --- |
| RFC 5277 event notifications | `Not implemented` | No `create-subscription`, no notification stream reader, and no event callback surface. |
| RFC 5277 `:interleave:1.0` | `Not implemented` | No interleaving of notifications with active RPC traffic. |

## Junos-specific and non-standard helpers

| Feature | Status | Notes |
| --- | --- | --- |
| Junos `<load-configuration>` helpers | `Supported` | XML, text, and set-style configuration loading helpers are present. These are vendor-specific, not portable NETCONF. |
| Junos `<commit-configuration><full/>` | `Supported` | `commitFull()` exists and is Junos-specific. |
| Junos CLI command helper | `Supported with caveats` | `runCliCommand(...)` and `runCliCommandRunning(...)` are implemented, but they are not portable to non-Junos servers. |
| Junos configuration mode helpers | `Supported` | `openConfiguration(...)` and `closeConfiguration()` are implemented. |
| SSH exec shell helpers | `Supported with caveats` | `runShellCommand(...)` and `runShellCommandRunning(...)` now connect and clean up channels correctly, and reads are bounded by `commandTimeout`. They still do not provide a richer per-command cancellation model beyond timeout and channel close. |
| Device reboot helper | `Supported with caveats` | `reboot()` exists as a convenience helper, but it is server-specific rather than a portable standards abstraction. |

## Interoperability caveats worth knowing

- Default optional capability advertisement still uses legacy `urn:ietf:params:netconf:base:1.0#...` forms in `Device.DEFAULT_CLIENT_CAPABILITIES`. Many servers accept these, but strict RFC 6241 capability matching may not.
- Candidate, validate, and confirmed-commit flows are now capability-gated before the RPC is sent. Other optional operations are still not enforced uniformly, especially outside the classic RFC 6241 capability set.
- `NetconfSession` should be treated as a single sequential request/response channel. Use separate sessions for concurrent workflows.
- The SSH transport is still tightly coupled to JSch. That preserves the current JSch-based deployment model, including existing FIPS-oriented environments, but transport abstraction is future work rather than current behavior.

## Primary implementation points

- [`Device`](../src/main/java/net/juniper/netconf/Device.java) - SSH transport setup, client capability advertisement, device-level helpers
- [`NetconfSession`](../src/main/java/net/juniper/netconf/NetconfSession.java) - NETCONF framing, message-id handling, core RPC helpers, session lifecycle
- [`Hello`](../src/main/java/net/juniper/netconf/element/Hello.java) - `<hello>` parsing and generation
- [`RpcReply`](../src/main/java/net/juniper/netconf/element/RpcReply.java) - `<rpc-reply>` and `<rpc-error>` parsing
- [`Datastore`](../src/main/java/net/juniper/netconf/element/Datastore.java) - datastore enum used by NMDA-style `<get-data>`
