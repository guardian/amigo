# Logstash

Installs the Logstash sidecar on the machine.

This is useful if you need to send logs from a 3rd-party process (e.g. nginx or Elasticsearch) to your ELK stack.

For your application logs, it's probably better to send them to Kinesis stream and have ELK pick them up from there.
The Logstash sidecar process is known to have some reliability issues.