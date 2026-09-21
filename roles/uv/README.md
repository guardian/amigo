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

### Configuring a recipe in the AMIgo UI

On the recipe edit page (for example, `/recipes/cuda-ami-test/edit`), select the
`uv` role and paste this single line into its **Custom variables** box to install
WhisperX 3.8.5 with Python 3.10:

```text
uv_tools: '{{ [{"package": "whisperx==3.8.5", "python": "3.10"}] }}'
```

Keep the outer single quotes and the `{{ ... }}` expression: the UI accepts a
quoted string here, which Ansible evaluates into the list of tool objects.
The UI does not accept a list of objects directly. Additional tools can be
added inside the same expression, each with its own `python` value.

For example, to install WhisperX alongside OCRmyPDF with the RapidOCR plugin,
use this line in the same **Custom variables** box:

```text
uv_tools: '{{ [{"package": "whisperx==3.8.5", "python": "3.10"}, {"package": "ocrmypdf", "python": "3.12", "extra_args": ["--with", "ocrmypdf-rapidocr==v.v.v"]}] }}'
```

Replace `v.v.v` with the plugin version you want to install. The `extra_args`
belong only to the OCRmyPDF entry, so the role runs the equivalent of:

```sh
uv tool install whisperx==3.8.5 --python 3.10
uv tool install ocrmypdf --python 3.12 --with ocrmypdf-rapidocr==v.v.v
```

RapidOCR is installed into OCRmyPDF's environment; WhisperX keeps its own
environment without that plugin. Keep `"--with"` and the package requirement
as separate list elements. Choose OCRmyPDF and plugin versions compatible
with each other and the selected Python version.

Optional `extra_args` are individual arguments passed to `uv tool install`, for
example `extra_args: ["--with", "some-dependency==1.2.3"]`. When migrating an
existing machine with `/usr/local/bin/whisperx` already owned by the old role,
use `extra_args: ["--force"]` for the migration to replace that executable.
Remove that flag afterwards to retain normal repeat-run behavior. Fresh image
builds do not need it.

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
