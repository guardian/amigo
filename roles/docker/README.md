# Docker

Installs Docker, takes a parameter `version` for the full docker version number, formatted as `version: '[version_here]'`, eg: `version: '5:24.0.6-1'`.

NB: if you are using Amazon Linux, use the `docker-ecr` role instead, which also
configures Docker to use AWS credentials if found.
