# Node.js

Installs Node.js.

## Variables

#### Preferred Approach

Using this approach the binary will be unpacked to `/usr/local/node` and a `node` symlink added to `/usr/bin`

* `node_full_version` - which version of Node.js (major.minor.patch format) to be downloaded from nodejs.org. No default value. NOTE: this uses the `linux-x64` version from https://nodejs.org/dist/

* `packages` - npm packages to install globally. e.g. `packages: [pm2, reqwest]`

#### DEPRECATED Approach

* `node_version` which version of Node.js to install (from nodesource.com - unofficial). Default value is `4.x`. Consult https://github.com/nodesource/distributions for valid values.
