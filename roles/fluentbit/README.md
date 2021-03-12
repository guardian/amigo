# Fluentbit

**Ubuntu only.**

Fluentbit is a fast and small logs processor. The [official
manual](https://docs.fluentbit.io/manual/) provides a good overview.

## Configure role

    ubuntu_version: xenial | bionic | focal

The default is `bionic` but we recommend you set this var explicitly in Amigo.

## On your instance...

To start the agent:

    $ service td-agent-bit start

By default it loads config from `/etc/td-agent-bit/td-agent-bit.conf` so
overwrite that with your own config in your userdata/startup scripts.

The service itself is called `td-agent-bit` and is a normal systemd service.
E.g.

    $ service td-agent-bit [start | stop | status]
