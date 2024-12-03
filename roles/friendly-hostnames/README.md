Friendly Hostnames
===========

Most servers are like 
[cattle](https://devops.stackexchange.com/questions/653/what-is-the-definition-of-cattle-not-pets) 
and it isn't necessary to name them. Sometimes you want to treat them more like a pet, giving them a human readable name.  
Perhaps they hold data meaning there is a risk associated with indiscriminately killing them and replacing them with 
another. You may benefit from knowing which server you have up at a given time.

The feature sets the hostname for your server and applies an AWS "Name" tag. The name is chosen from a list of ~3000 feminists names.
        
You will need to run the following script in your cloud init

```
/opt/features/friendly-hostnames/set-hostname.sh
```

A hostname will be chosen at random and formatted to a 
[valid hostname](https://en.wikipedia.org/wiki/Hostname#Restrictions_on_valid_hostnames), 
only including the characters [^A-Za-z].  

You might need to consider the [birthday problem](https://en.wikipedia.org/wiki/Birthday_problem) when choosing your 
list size. For a cluster of size `s`, and a probability of `p` of a name clash (hopefully low, less than `0.05`), you'll
need a hostnames list of length `s^2 / 2 * p`.
