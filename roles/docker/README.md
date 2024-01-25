# Docker

Installs Docker in line with documentation on here https://docs.docker.com/engine/install/ubuntu/. Including the following
packges:

- docker-ce
- docker-ce-cli
- containerd.io
- docker-buildx-plugin
- docker-compose-plugin

This role will install the latest version of Docker - currently not configurable.

NB: if you are using Amazon Linux, use the `docker-ecr` role instead, which also configures Docker to use AWS credentials
if found.
