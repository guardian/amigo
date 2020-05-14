# Attach EBS Volume

A script to mount ebs volumes, because it's not safe to assume a device path.
On aws "nitro" instances, NVM is used, and the mapping between nvm device path
and EBS path is non-deterministic. 


Usage: attach-ebs-volume -d device-letter -m mountpoint [-u user]

  This script creates and attaches an encrypted EBS volume.  

    -d dev-letter The device letter. This should be a single character (usually
                  h or later) that is used to identify the device. Note that the
                  device name specified by Amazon and understood by Ubuntu are
                  different.
                  (e.g. Specifying h will appear as /dev/sdh in Amazon and map
                  to /dev/xvdh under Ubuntu, if you're using nitro your path in 
                  Ubuntu will be /dev/nvmeXn1 where X ∈ ℝ hopefully).

    -m mountpoint The fs mountpoint (will be created if necessary).

    -u user       [optional] chown the mountpoint to this user.

    -o options    [optional] Specify file system options (defaults to "defaults")

    -h            Displays this help message. No further functions are
                  performed. 
