#!/usr/bin/env python
jsonfile = "/etc/gu/tags.json"
propfile = "/etc/gu/tags.properties"

import boto3
import json
import requests

def get_instance_id():
    requests.get('http://169.254.169.254/latest/meta-data/instance-id', timeout=1).text

session = boto3.Session(profile_name='deployTools', region_name='eu-west-1')

def get_instance_tags(id):
    ec2_client = session.resource('ec2')
    ec2instance = ec2_client.Instance(id)
    return ec2instance.tags:

def get_asg_tags(id, next_token=None):
    asg_client = session.client('autoscaling')
    if next_token:
       asgs = asg_client.describe_auto_scaling_groups(NextToken=next_token)
    else:
       asgs = asg_client.describe_auto_scaling_groups()
    for asg in asgs['AutoScalingGroups']:
       for instance in asg['Instances']:
          #print "random instnace", instance['InstanceId']
          if instance['InstanceId'] == id:
             return asg['Tags']
    if 'NextToken' in asgs.keys():
       return get_asg_tags(id, asgs['NextToken'])
    return None

def get_tags(id):
    tags = get_asg_tags(id)
    if tags is None:
        return get_instance_tags(id)
    else:
        formatted_tags={}
        for tag in tags:
            if not tag['Key'].startswith('aws:'):
                formatted_tags[tag['Key']] = tag['Value']
        return formatted_tags

instance_id=get_instance_id()
tags = get_tags(instance_id)

file = open(jsonfile, "rw")
file.write(json.dumps(tags))
file.close()

file = open(propfile, "rw")
for k in tags.keys():
    file.write(k + ": " + a[k])
file.close()
