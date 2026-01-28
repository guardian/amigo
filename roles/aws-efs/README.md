# aws-efs

This installs the [`amazon-efs-utils`](https://docs.aws.amazon.com/efs/latest/ug/using-amazon-efs-utils.html) package.

It allows for EFS filesystems to be mounted using the `efs` filesystem type.

Works with Debian and Redhat images.

**NOTE**: This role needs loads of memory because of all the rust etc being compiled. You'll need to use a base image
with the 'Requires XLarge builder instance' option selected.

# Usage

You need to set `efs_utils_version` in your recipe parameters - the last tested version is in the defaults/main.yml file 
but you should at least try to use the latest - you can see releases here https://github.com/aws/efs-utils/tags

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
