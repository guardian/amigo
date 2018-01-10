# packages

Install package(s) with `apt-get` or `yum`. The `packages` parameter specifies which package(s) are installed, e.g.

```
packages: [package-1, package-2]
```

It is also possible to install packages (only on Debian) from an S3 bucket (that is configured in the AMIgo configuration):

```
s3_packages: [package1.deb, package2.deb]
```