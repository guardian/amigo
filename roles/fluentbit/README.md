# Fluentbit

**Ubuntu only.**

Fluentbit is a fast and small logs processor. The [official
manual](https://docs.fluentbit.io/manual/) provides a good overview.

## Configure role

    version: newest # latest available package, or an exact version such as '5.1.2'
    ubuntu_version: bionic # use resolute for Ubuntu 26.04
    fluentbit_package: td-agent-bit # use fluent-bit for Ubuntu 26.04

Set the repository and package explicitly in Amigo when using this role directly.
`cdk-base` selects these automatically. `newest` (the default) and `latest`
install the latest available package; exact versions must exist in the selected
repository for the target architecture.

## On your instance...

To start the agent:

    $ service td-agent-bit start

By default it loads config from `/etc/td-agent-bit/td-agent-bit.conf` so
overwrite that with your own config in your userdata/startup scripts.

With `fluentbit_package: fluent-bit`, the service is `fluent-bit` and its
configuration is `/etc/fluent-bit/fluent-bit.conf`. The `cdk-base` role provides
compatibility links for the old service and configuration paths on Ubuntu 26.04.
