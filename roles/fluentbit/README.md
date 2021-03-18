# Fluentbit

**Ubuntu only.**

Fluentbit is a fast and small logs processor. The [official
manual](https://docs.fluentbit.io/manual/) provides a good overview.

## Configure role

    version: 1.7.2. # for example, defaults to 'newest'
    ubuntu_version: xenial | bionic | focal # defaults to bionic

We recommend you set both vars explicitly in Amigo.

## On your instance...

To start the agent:

    $ service td-agent-bit start

By default it loads config from `/etc/td-agent-bit/td-agent-bit.conf` so
overwrite that with your own config in your userdata/startup scripts.
