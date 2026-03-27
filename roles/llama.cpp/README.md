# llama.cpp role
This role installs [llama.cpp](https://github.com/ggerganov/llama.cpp) with CUDA support using
[Nix](https://nixos.org/), and downloads GGUF model files so that models can be run offline.

This role expects the AWS Deep Learning AMI as the base image, which provides CUDA drivers and toolkit out of the box.

## How it works
1. Installs the Nix package manager (daemon mode)
2. Installs `llama-cpp` from nixpkgs with `cudaSupport = true`
3. Symlinks `llama-cli` and `llama-server` into `/usr/local/bin`
4. Downloads the configured GGUF model(s) from Hugging Face

## Configuration

### llama_cpp_models
A list of models to download from Hugging Face. Each entry should have a `repo` and `file` key.
The `repo` is the Hugging Face repository path and `file` is the GGUF filename to download. See
https://github.com/ggml-org/llama.cpp?tab=readme-ov-file#obtaining-and-quantizing-models for the docs on getting models
for llama.cpp

Default:
```yaml
llama_cpp_models:
  - repo: Qwen/Qwen3-8B-GGUF
    file: qwen3-8b-q4_k_m.gguf
```

You can swap models or add additional ones, for example:
```yaml
llama_cpp_models:
  - repo: Qwen/Qwen3-4B-GGUF
    file: qwen3-4b-q8_0.gguf
  - repo: bartowski/Llama-3.2-3B-Instruct-GGUF
    file: Llama-3.2-3B-Instruct-Q8_0.gguf
```

### target_dir
The directory where models are stored.

Default: `/opt/llama`

## Installed binaries
The role symlinks two binaries into `/usr/local/bin`:

- **`llama-cli`** — interactive CLI for running inference
- **`llama-server`** — HTTP API server compatible with the OpenAI chat completions format

## Example usage
Run the model interactively:
```bash
llama-cli -m /opt/llama/models/qwen3-8b-q4_k_m.gguf -ngl 99 -p "Hello!"
```

Start the API server:
```bash
llama-server -m /opt/llama/models/qwen3-8b-q4_k_m.gguf -ngl 99 --port 8080
```

The `-ngl 99` flag offloads all layers to the GPU for maximum performance.

