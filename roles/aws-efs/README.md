# aws-efs

This installs the [`amazon-efs-utils`](https://docs.aws.amazon.com/efs/latest/ug/using-amazon-efs-utils.html) package.

It allows for EFS filesystems to be mounted using the `efs` filesystem type.

Works with Debian and Redhat images.

# Usage

Add to `roles/<MY_ROLE>/meta/main.yaml`:

```yaml
---
dependencies:
  [...]
  - role: aws-efs
  [...]
```
