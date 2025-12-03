# Node.js

Installs Node.js.

## Role variables

### node_major_version (recommended)

The major version of Node.js to install. The role will automatically fetch and install the latest version within that major release from nodejs.org.

```yaml
node_major_version: 24  # Installs latest v24.x.x
```

If not specified, defaults to value in `defaults/main.yml`.

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
**Note**: Package installation is only supported when using `node_major_version` or `node_full_version`. It does not work with the deprecated `node_version` (nodesource) installation method.


## Installation

If no variables are specified, the role defaults to installing the latest Node.js v24.x for x64 architecture (as defined in `defaults/main.yml`).

The binary will be unpacked to `/usr/local/node-v{version}-linux-{architecture}/` and symlinks are created:
- `/usr/bin/node` → points to the node binary
- `/usr/local/node` → points to the bin directory containing node, npm, and npx

Node binaries (node, npm, npx) are accessible at `/usr/local/node/`. The `node` binary is also linked in `/usr/bin` so should be available via the `PATH` environment variable.


## DEPRECATED variables

### node_full_version

**DEPRECATED**: Use `node_major_version` instead to automatically receive security patches within a major version.

If you need a specific full version of Node.js (e.g., for reproducible builds), you can specify the exact version. This uses the same installation method as `node_major_version` but skips the automatic version lookup.

```yaml
node_full_version: 20.11.0
```

### node_version 

**DEPRECATED**: Use `node_major_version` instead.

The version of node to be made available by [nodesource](https://github.com/nodesource/distributions/blob/master/README.md). This method installs Node.js via apt package manager instead of downloading from nodejs.org.

```yaml
node_version: 20.x
```

**Note**: This installation method does not support the `packages` variable for installing global npm packages.