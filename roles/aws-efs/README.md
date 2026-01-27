# aws-efs

This installs the [`amazon-efs-utils`](https://docs.aws.amazon.com/efs/latest/ug/using-amazon-efs-utils.html) package.

It allows for EFS filesystems to be mounted using the `efs` filesystem type.

Works with Debian and Redhat images.

**NOTE**: This role needs loads of memory because of all the rust etc being compiled. You'll need to use a base image
with the 'Requires XLarge builder instance' option selected.

# Usage

Add to `roles/<MY_ROLE>/meta/main.yaml`:

```yaml
---
dependencies:
  [...]
  - role: aws-efs
  [...]
```

## Why are we compiling this from source?!
Apparently this is [what amazon tells us to do](https://docs.aws.amazon.com/efs/latest/ug/installing-amazon-efs-utils.html#installing-other-distro)
