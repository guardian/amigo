# pip3-packages

Install Python 3 package(s) with `pip`. The `packages` parameter specifies which
package(s) are installed, e.g.

```
packages: [flask, ocrmypdf]
```

## Extra arguments
You may also pass extra arguments to `pip` by setting `pip_extra_args`, for example

```
packages: [torch==2.0.0 torchvision==0.15.1 torchaudio==2.0.1], pip_extra_args: '--index-url https://download.pytorch.org/whl/cu118'
```
