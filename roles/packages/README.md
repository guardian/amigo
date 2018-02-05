# packages

Install package(s) with `apt-get` or `yum`. The `packages` parameter specifies which package(s) are installed, e.g.

```
packages: [package-1, package-2]
```

It is also possible to install packages (only on Debian) from an S3 bucket (that is configured in the AMIgo configuration):

```
s3_packages: [package1.deb, package2.deb]
```

If you need to add a new repository for any of your packages you can do so:

```
repositories: { elasticsearch: 'deb https://artifacts.elastic.co/packages/6.x/apt stable main' }
```

When you install a new repository a `apt-get update` will be run.

Likewise, if you need to add a signing key you can add that with:

```
signing_keys: ['https://artifacts.elastic.co/GPG-KEY-elasticsearch']
```