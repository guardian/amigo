# Fluentbit

**Works on Ubuntu Bionic (x86 or ARM) only.**

Fluentbit is a fast and small logs processor. The [official
manual](https://docs.fluentbit.io/manual/) provides a good overview.

To start the agent:

    $ service td-agent-bit start

By default it loads config from `/etc/td-agent-bit/td-agent-bit.conf` so
overwrite that with your own config in your userdata/startup scripts.

The service itself is called `td-agent-bit` and is a normal systemd service.
E.g.

    $ service td-agent-bit [start | stop | status]
