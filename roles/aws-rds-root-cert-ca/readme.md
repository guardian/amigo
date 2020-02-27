# aws-rds-root-ca-certs

## Description
Sets up an AWS RDS root CA certificate in a Java truststore if an app wants to use full SSL between an RDS database.

## Variables
| Variable | Description |
| - | - |
| `certificate_source` | url of the rds location |
| `certificate_name` | name of the certificate (without the pem extension) |
| `truststore_dir` | dir of the default Java truststores |
| `truststore_name` | name of the new truststore |

## Defaults
| Default | Description |
| - | - |
| `truststore_password` | password for the new truststore |
| `castore_password` | password for the default truststore. It is unlikely this needs changing. |
