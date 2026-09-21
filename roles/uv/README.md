# uv

Installs `uv`, which is:

["An extremely fast Python package and project manager, written in Rust"](https://docs.astral.sh/uv/)

For the applications whose dependencies it manages, it will automatically download and install the appropriate Python version, if not present in the environment. So if using `uv`, you don't necessarily need to bake in the right Python version.

## Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `uv_major_version` | `0` | The major version of uv to install |
| `uv_minor_version` | `9` | The minor version of uv to install |
| `uv_tools` | `[]` | Tools to install; each entry has a `package`, optional `python`, and optional `extra_args` list |

The role will install the latest version matching `=={major}.{minor}.*`, to allow for auto-updating to the latest patch version.

As stated in https://docs.astral.sh/uv/reference/policies/versioning/, "uv uses a custom versioning scheme in which the minor version number is bumped for breaking changes, and the patch version number is bumped for bug fixes, enhancements, and other non-breaking changes."

## Installing command-line tools

Run this role as root (for example, with `become: true`), as with the other
system installation roles. Each tool is installed using `uv tool install` into
its own persistent environment under `/opt/uv/tools`, with commands installed
into `/usr/local/bin` and managed Python versions under `/opt/uv/python`.
These locations make its commands
available to all users without activating a virtual environment or changing
shell startup files. Managed Python installations are also stored outside the
installing user's home directory so other users can access them.

Optional `extra_args` are individual arguments passed to `uv tool install`, for
example `extra_args: ["--with", "some-dependency==1.2.3"]`. When migrating an
existing machine with `/usr/local/bin/whisperx` already owned by the old role,
use `extra_args: ["--force"]` for the migration to replace that executable.
Remove that flag afterwards to retain normal repeat-run behavior. Fresh image
builds do not need it.

For manual tool maintenance, use the same `UV_TOOL_DIR`, `UV_TOOL_BIN_DIR`, and
`UV_PYTHON_INSTALL_DIR` environment variables as the role. The commands installed
by the role do not need those variables to run.

See the [uv tools documentation](https://docs.astral.sh/uv/concepts/tools/) for
Python selection, environment isolation, and tool version behavior.

## WARNING: Use with caution!
In general it is **not recommended** to install your dependencies at instance launch time. It has the following downsides:

1. It increases the time it takes for your instance to launch
2. It makes deploys less deterministic, i.e. different versions could get installed from the same build being re-deployed. (For this reason, you should **always** include your `uv.lock` file in the deploy artifact when using this role.)
3. It opens a new and less visible vector for a supply chain attack, because the record of exactly what code got installed is only on the box and not within the CI/CD pipeline.
4. It makes deployments (and instance replacements, or scale-up events) less reliable. If the repository hosting a dependency goes down, then we cannot deploy, scale-up or replace instances until someone fixes it.

### What is a better approach?
Consider using Docker to bundle your Python code and its dependencies. You can use the [Docker role](roles/docker) if you're deploying to EC2, although a better approach would try and deploy using the dedicated AWS container service, [ECS](https://docs.google.com/document/d/1byBPP0_l5s1CbeGV3fl07RWWmOPKAWtuaKDstIXzpJ8/edit?tab=t.0#heading=h.ggp1ujl5kkw9).   
