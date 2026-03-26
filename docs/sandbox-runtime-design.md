# Sandbox-First Runtime Design

## Goal

Give `manus` a predictable execution environment for shell commands, script execution, dependency installation, and skill runtime work without depending on ad-hoc host machine state.

## Current Direction

The system now has the basic building blocks for a usable Docker sandbox:

- Configurable sandbox image
- Configurable shell executable and shell option
- Optional workspace bind mount
- Optional image auto-pull
- Existing `runSandboxCommand` local tool

This is enough to move toward a `sandbox-first` execution model.

## Runtime Strategy

Recommended execution priority for `manus`:

1. Use `runSandboxCommand` for bash/sh, Python, Node.js, uv, and script execution.
2. Use host shell tools only when the task needs host-only behavior.
3. Ask for human input only after both runtime paths are genuinely insufficient.

This keeps ordinary execution isolated while still preserving a host fallback.

## Why Sandbox First

- Reduces dependency drift on the host machine
- Makes skill execution more reproducible
- Gives `manus` a stable place to run generated scripts
- Avoids repeatedly teaching the model which local runtime happens to exist
- Makes future policy controls simpler

## Recommended Agent Runtime Image

The default `python:3.12-slim` image is enough for smoke testing, but not for real agent work. A dedicated runtime image should include:

- `bash`
- `python`
- `pip`
- `uv`
- `node`
- `npm`
- `git`
- `ripgrep`
- `curl`
- `ca-certificates`
- `pandoc` or other document tooling if office conversion is a common path

Suggested image name:

- `openmanus/agent-runtime:base`

## Workspace Contract

The host workspace should be mounted into the container so that:

- skills can consume artifacts produced by earlier steps
- generated scripts can be stored in the same workspace as the final outputs
- exported files such as `.docx`, `.pdf`, and reports flow back to the host automatically

Recommended mount path inside the container:

- `/workspace`

## Recommended Configuration

```yaml
openmanus:
  sandbox:
    enabled: true
    image: openmanus/agent-runtime:base
    working-directory: /workspace
    mount-workspace: true
    workspace-mount-path: /workspace
    auto-pull-image: false
    shell-executable: bash
    shell-option: -lc
    memory-limit: 1g
    timeout-seconds: 120
    network-enabled: true
```

Notes:

- `auto-pull-image: false` is recommended after the image is preloaded in the deployment environment.
- `network-enabled: true` is needed if the sandbox should install runtime dependencies on demand.
- For locked-down environments, keep `network-enabled: false` and pre-bake dependencies into the image.

## Tooling Model

The current tool model can stay unchanged:

- `runSandboxCommand` remains the sandbox execution entry point
- `runPowerShell` remains the host execution fallback
- file tools remain shared helpers for workspace inspection and artifact management

No skill or tool schema changes are required for this phase.

## Near-Term Improvements

### Phase 1

- Enable sandbox in deployment where Docker is available
- Provide a real agent runtime image
- Bias `manus` prompt toward sandbox-first execution

### Phase 2

- Reuse a sandbox container per session instead of one container per command
- Cache downloaded dependencies inside the sandbox workspace or image layer
- Add a lightweight health check endpoint for sandbox readiness

### Phase 3

- Route certain skill or tool executions to sandbox automatically
- Add policy-based fallback from sandbox to host
- Track runtime provenance in workflow steps

## Non-Goals for This Phase

- Changing skill definitions
- Changing tool schemas
- Building a full dependency preparation service
- Introducing session-level sandbox orchestration

This phase is only about making sandbox execution a first-class default runtime for `manus`.