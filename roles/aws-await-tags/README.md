Await Tags
==========

This role should ideally not be used for new projects.

We use EC2 [Tagging](https://docs.aws.amazon.com/cli/latest/reference/ec2/describe-tags.html)
to look up the `Stack`, `App` and `Stage` for a given instance.

Unfortunately, the API is eventually consistent and can return empty tags on some AWS
instance types that boot fast enough. This can cause major problems with apps that are
written to assume a lack of tags means run in `DEV` mode.

The correct solution to the problem is to change the application to receive the information
via another mechanism eg subbing in environment variables in launch configuration.

This role exists so we can get the safety of consistently reading tags before we have had
a chance to change the code in all the projects.

It simply adds a script to Cloud Init [Scripts Per Boot](http://cloudinit.readthedocs.io/en/latest/topics/modules.html#scripts-per-boot)
that tries to get the tags in a loop.

NB: if tags are not found the `user-data` script **will still run**.