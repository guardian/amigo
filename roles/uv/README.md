# uv

Installs `uv`, which is:

["An extremely fast Python package and project manager, written in Rust"](https://docs.astral.sh/uv/)

Despite managing Python packages, it doesn't require Python because it's written in Rust.

For the applications whose dependencies it manages, it will automatically download and install the appropriate Python version, if not present in the environment. So if using `uv`, you don't necessarily need to bake in the right Python version.

## WARNING: Use with caution!
In general it is **not recommended** to install your dependencies at instance launch time. It has the following downsides:

1. It increases the time it takes for your instance to launch
2. It makes deploys less deterministic, i.e. different versions could get installed from the same build being re-deployed. (For this reason, you should **always** include your `uv.lock` file in the deploy artifact when using this role.)
3. It opens a new and less visible vector for a supply chain attack, because the record of exactly what code got installed is only on the box and not within the CI/CD pipeline.
4. It makes deployments (and instance replacements, or scale-up events) less reliable. If the repository hosting a dependency goes down, then we cannot deploy, scale-up or replace instances until someone fixes it.

### What is a better approach?
Consider using Docker to bundle your Python code and its dependencies. You can use the [Docker role](roles/docker) if you're deploying to EC2, although a better approach would try and deploy using the dedicated AWS container service, [ECS](https://docs.google.com/document/d/1byBPP0_l5s1CbeGV3fl07RWWmOPKAWtuaKDstIXzpJ8/edit?tab=t.0#heading=h.ggp1ujl5kkw9).   
