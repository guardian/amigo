# Node.js

Installs Node.js.

## Role variables

### `node_major_version` (recommended)

The major version of Node.js to install. The role will automatically fetch and install the latest version within that major release from nodejs.org.

```yaml
node_major_version: 24  # Installs latest v24.x.x
```

- This variable (or `node_full_version`) is required; the role will fail early with a clear message if no version variable is provided.

### architecture (default: x64)

The CPU architecture for the Node.js download. Valid values: `x64`, `arm64`, `armv7l`, `ppc64le`, `s390x`.

```yaml
architecture: arm64  # For ARM-based systems like Graviton
```

If not specified, defaults to `x64`.

### packages (optional)

A list of npm packages to install globally after Node.js is installed.

```yaml
packages: [pm2, reqwest, typescript]
```

## Installation

If the architecture variable is not specified, the role defaults to installing x64 architecture (as defined in `defaults/main.yml`).

The binary will be unpacked to `/usr/local/node-v{version}-linux-{architecture}/` and symlinks are created:
- `/usr/bin/node` → points to the node binary
- `/usr/local/node` → points to the bin directory containing node, npm, and npx

Node binaries (node, npm, npx) are accessible at `/usr/local/node/`. The `node` binary is also linked in `/usr/bin` so should be available via the `PATH` environment variable.


## DEPRECATED variables

### `node_full_version` (deprecated)

Deprecated: Use `node_major_version` instead to automatically receive security patches within a major version.

If you temporarily need a specific full version of Node.js (e.g. when a new automatically selected Node version has a compatibility issue), you can specify the exact version using this variable. This uses the same installation method as `node_major_version` but skips the automatic version lookup.

```yaml
node_full_version: 20.11.0
```

### `node_version` (removed)

Removed: the old nodesource-based `node_version` variable and installation path have been removed from this role. Use `node_major_version` (or `node_full_version`) instead.
