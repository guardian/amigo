# otel-collector-arm

## What does this do?

It installs the AWS OpenTelemetry collector (https://github.com/aws-observability/aws-otel-collector) onto the target box, without configuration.

You must make sure you use the right architecture, this is the ARM version for Graviton instances. Use the x86 version
if you have an Intel instance.

The OTEL collector can be used for shipping Prometheus compatible metrics, Amazon X-ray traces etc. to centralised
data sources

## How do I use it?

By itself, applying this role will not start anything up on your instance.

Once Amigo has installed the collector on your base image, you'll need to supply a configuration file during your instance's
cloudformation setup.

The collector lives in the path `/opt/aws/aws-otel-collector` and has its own startup script which then chains onto systemd
(I know, I know..... it wasn't me that designed it to work like that!)

At the end of your instance userdata script, you should start up the collector and point it to your configuration file like this:

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

The line `- targets: [ '0.0.0.0:9000' ]` tells the collector to connect to port 9000 on _your app_ and make an HTTP
request to the `/metrics` endpoint on that port.  It expects to receive a Prometheus metrics output in that response.
For more details on the prometheus metrics format see https://prometheus.io/docs/prometheus/latest/getting_started/.

This endpoint can be provided manually or it can be added via a Java collector or library - for example https://aws-otel.github.io/docs/getting-started/java-sdk/trace-auto-instr.

For more information on setting your app up with instrumentation, please contact DevX directly to discuss your requirements.

You will also need to **add permissions to allow your instance to write into Prometheus**.  This can be done
by allowing the `aps:RemoteWrite` permission in your instance's default role.

The service status can be checked by running:

```bash
sudo /opt/aws/aws-otel-collector/bin/aws-otel-collector-ctl  -a status
```
on your instance.

OTEL collector logs can be found by running:

```bash
sudo journalctl -u aws-otel-collector -f 
```
as per usual.

### Enabling debug logging

If you want to enable debug logging for the collector, you can do it by adding a key-value pair to the file
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
