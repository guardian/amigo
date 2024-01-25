# docker-pull
This is a very simple role to pre-cache docker images on an AMI baked by AMIgo. This can be particularly helpful for larger
containers, to speed up boot time etc. 

The only parameter is 'images' - which you can use to specify a list of images to pull. Bear in mind that unless you pin
to a specific tag, 'docker run' may end up pulling the latest version of the image at runtime, which may not be what you want.

Example: `images: ['ghcr.io/guardian/transcription-service:whisper-docker']`
