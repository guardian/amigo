# logstash-deb

Install logstash and optionally install logstash plugins. 

Basic use allows the version of logstash to be specified:

```
version: 6.1.1
```

You can additionally install plugins:

```
version: 6.1.1, plugins: [logstash-input-kinesis]
```

It is also possible to install plugins from gems in an S3 bucket (that is configured in the AMIgo configuration):

```
version: 6.1.1, s3_plugins: [logstash-input-kinesis-2.0.7.gem]
```