#!/bin/bash

set -e

script_dir=$(dirname "$0")
target_dir=/home/ubuntu
custom_playbook="playbook-custom.yaml"
vm_name=amigo-test

if ! command -v multipass &> /dev/null
then
    echo Installing multipass...
    brew install --cask multipass
fi

if [ ! -e "$script_dir/$custom_playbook" ]
then
    echo "Creating local playbook file ($custom_playbook)..."
    cp $script_dir/playbook.yaml $script_dir/playbook-custom.yaml
    echo "A local playbook file ($custom_playbook) has been created. Edit this file and then run $0 again to execute it."
    exit 0
fi

if ! multipass list | grep $vm_name | grep Running > /dev/null
then
    echo 'Creating and provisioning multipass VM...'
    multipass launch --name $vm_name 20.04
    multipass mount $script_dir/../roles $vm_name:$target_dir/.ansible/roles
    multipass mount $script_dir $vm_name:$target_dir/test
    multipass exec $vm_name -- $target_dir/test/bootstrap-vm.sh
fi

multipass exec $vm_name -- ansible-playbook /home/ubuntu/test/$custom_playbook
