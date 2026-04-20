# Cuda Cleanup Role
This role is for use with the AWS Deep Learning AMI. By default this AMI comes with multiple versions of the CUDA drivers
installed, taking up a lot of disk space. This role removes all but one specified version passed via desired_cuda_version
to free up disk space. This is particularly important when using an Amazon EBS Provisioned Rate for Volume Initialization
as GB means $. Each version of CUDA is around 10GB.
