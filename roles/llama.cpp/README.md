# llama.cpp role
This role installs [llama.cpp](https://github.com/ggml-org/llama.cpp), relying on a built version of llama.cpp created by 
https://github.com/guardian/transcription-service/blob/main/.github/workflows/build-llama-cpp.yaml

You can control which build of llama.cpp gets baked in via the llama_cpp_build_stage flag and the riffraff project 
`investigations::transcription-service-llama-cpp`.

This role expects the AWS Deep Learning AMI as the base image, which provides CUDA drivers and toolkit out of the box.
