# Redirect Logs

A small utility script for moving the location of `/var/log`.

When combined with the `mount-drive` and an encrypted EBS volume this can be useful for GDPR compliance.

## Example Cloudformation Usage:

```yaml
BlockDeviceMappings:
  - DeviceName: "/dev/sdk"
    Ebs:
      VolumeSize: 10
      VolumeType: gp2
      Encrypted: true

UserData:
  Fn::Base64:
    /opt/mount-drive/mount-drive.sh /dev/xvdk /encrypted
    /opt/redirect-logs/redirect-logs.sh /encrypted
```
Here we define a block device `/dev/sdk`, mount it using the `mount-drive` role and then redirect our logs there.
