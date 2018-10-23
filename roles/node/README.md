# Node.js

Installs Node.js.

## Variables

#### Preferred Approach

Using this approach the binary will be unpacked to `/usr/local/node` and a `node` symlink added to `/usr/bin`

* `node_full_version` - which version of Node.js (major.minor.patch format) to be downloaded from nodejs.org. No default value. NOTE: this uses the `linux-x64` version from https://nodejs.org/dist/

* `nodejs_dist_relative_path` - if the `node_full_version` param doesn't work or you need something other than the `linux-x64` version, with this parameter you can specify a path relative to https://nodejs.org/dist/

#### DEPRECATED Approach

* `node_version` which version of Node.js to install (from nodesource.com - unofficial). Default value is `4.x`. Consult https://github.com/nodesource/distributions for valid values.
