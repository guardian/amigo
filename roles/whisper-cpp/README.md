# Whisper.cpp role
This role aims to download, compile and install [whisper.cpp](https://github.com/ggerganov/whisper.cpp).

Whisper.cpp is a transcription service. There are a number of different models that can be used with whisper.cpp, with
different accuracy/performance tradeoffs. You can configure which models are baked into your AMI using the following parameters:

## whisper_models
This determines which standard whisper models are downloaded. You can find the full list of available models 
[here](https://github.com/ggerganov/whisper.cpp/tree/master/models#available-models).

Example: `whisper_models: [small, base.en]`

## whisper_distilled_model_paths
[distil-whisper](https://github.com/huggingface/distil-whisper) is a more performant version of whisper. To understand the
tradeoffs with using distil whisper see [github](https://github.com/huggingface/distil-whisper), 
[hugging face](https://huggingface.co/distil-whisper/distil-medium.en) and the [whisper.cpp models section](https://github.com/ggerganov/whisper.cpp/tree/master/models#distilled-models)

Generating the urls to download distilled models is a little tricky so you have to provide a full path. In a vague nod
towards security, the https://huggingface.co/distil-whisper/ prefix of the url is hard coded - you'll need to provide the
rest in a parameter

Example: `whisper_distilled_model_paths: ['distil-medium.en/resolve/main/ggml-medium-32-2.en.bin']`

## whisper_cpp_commit_hash
This is nothing to do with models. It should ideally be set to the commit hash of a 
[recent whisper.cpp release](https://github.com/ggerganov/whisper.cpp/releases)