# Docker ECR

**Use with an Amazon Linux 2 base image only.**

Installs and starts Docker with the
[ECR credentials helper](https://github.com/awslabs/amazon-ecr-credential-helper.).

IAM credentials will be automatically used when interacting with any Docker
registry. This only makes sense if you are pulling images/interacting with an
ECR registry.