# Node.js

Installs Node.js.

## Role variables

### node_major_version (default: 24)

The major version of Node.js to install. This is the **recommended approach**. The role will automatically fetch and install the latest version within that major release from nodejs.org.

```yaml
node_major_version: 24  # Installs latest v24.x.x
```

If not specified, defaults to `24` (current LTS version).

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

If no variables are specified, the role defaults to installing Node.js v24 for x64 architecture (as defined in `defaults/main.yml`).

Using this approach the binary will be unpacked to `/usr/local/node-v{version}-linux-{architecture}/` and a `node` symlink added to `/usr/bin`.

Node binaries (node, npm, npx) will be accessible at `/usr/local/node/`. The `node` binary is linked to in `/usr/bin` so should be available via the `PATH` environment variable.

## DEPRECATED variables

### node_full_version

**Deprecated**: Use `node_major_version` instead to automatically receive security patches.

If you need a specific full version of Node.js (e.g., for reproducible builds), you can specify the exact version.

```yaml
node_full_version: 20.11.0
```

### node_version 

**Deprecated**: Use `node_major_version` instead.

The version of node to be made available by [nodesource](https://github.com/nodesource/distributions/blob/master/README.md).

```yaml
node_version: 20.x
```