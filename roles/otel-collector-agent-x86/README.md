# otel-collector-agent-x86

## What does this do?

It installs the AWS OpenTelemetry agent (https://github.com/aws-observability/aws-otel-collector) onto the target box, without configuration.

The OTEL collector can be used for shipping Prometheus compatible metrics, Amazon X-ray traces etc. to centralised
data sources

## How do I use it?

By itself, applying this role will not start anything up on your instance.

Once Amigo has installed the agent on your base image, you'll need to supply a configuration file during your instance's
cloudformation setup.

The agent lives in the path `/opt/aws/aws-otel-collector` and has its own startup script which then chains onto systemd
(I know, I know..... it wasn't me that designed it to work like that!)

At the end of your instance userdata script, you should start up the agent and point it to your configuration file like this:

```bash
/opt/aws/aws-otel-collector/bin/aws-otel-collector-ctl -c </path/config.yaml> -a start
```

The configuration file should be YAML based, and look something like this:

```yaml
extensions:
  sigv4auth:
    service: "aps"  #APS refers to "Amazon Prometheus Service" aka Amazon Managed Prometheus aka AMP
    region: "eu-west-1"

receivers:
  prometheus:
    config:
      scrape_configs:
        - job_name: 'your-app-name'
          scrape_interval: 60s
          static_configs:
            # metrics endpoint for your app
            - targets: [ '0.0.0.0:9000' ]

processors:
  batch/metrics:
    timeout: 60s

exporters:
  prometheusremotewrite:
    endpoint: "{amazon managed prometheus write endpoint}"
    auth:
      authenticator: sigv4auth

service:
  pipelines:
    metrics:
      receivers: [prometheus]
      processors: [batch/metrics]
      exporters: [prometheusremotewrite]
```

You will also need to **add permissions to allow your instance to write into Prometheus**.  This can be done
by allowing the `aps:RemoteWrite` permission in your instance's default role.

The service status can be checked by running:

```bash
sudo /opt/aws/aws-otel-collector/bin/aws-otel-collector-ctl  -a status
```
on your instance.

OTEL agent logs can be found by running:

```bash
sudo journalctl -u aws-otel-collector -f 
```
as per usual.

### Enabling debug logging

If you want to enable debug logging for the agent, you can do it by adding a key-value pair to the file
`/opt/aws/aws-otel-collector/etc/extracfg.txt`:

```bash
echo "loggingLevel=DEBUG" | sudo tee -a /opt/aws/aws-otel-collector/etc/extracfg.txt
sudo /opt/aws/aws-otel-collector/bin/aws-otel-collector-ctl -a stop
sudo /opt/aws/aws-otel-collector/bin/aws-otel-collector-ctl -a start
```

## For more information

- https://aws-otel.github.io/docs/setup
- https://github.com/aws-observability/aws-otel-collector
- Talk to DevX :)
