# Mount Drive

Provides you AMI with a script for creating a mount point and mounting a drive.

## Example cloudformation usage:

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
```
Here we define a block device `/dev/sdk` and mount it as `/encrypted`.
