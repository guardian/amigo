# uv

Installs `uv`, which is:

["An extremely fast Python package and project manager, written in Rust"](https://docs.astral.sh/uv/)

Despite managing Python packages, it doesn't require Python because it's written in Rust.

For the applications whose dependencies it manages, it will automatically download and install the appropriate Python version, if not present in the environment. So if using `uv`, you don't necessarily need to bake in the right Python version.