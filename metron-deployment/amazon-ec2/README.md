Apache Metron on Amazon EC2
===========================

This project fully automates the provisioning of Apache Metron on Amazon EC2 infrastructure.  Starting with only your Amazon EC2 credentials, this project will create a fully-functioning, end-to-end, multi-node cluster running Apache Metron.

Warning: Amazon will charge for the use of their resources when running Apache Metron.  The amount will vary based on the number and size of hosts, along with current Amazon pricing structure.  Be sure to stop or terminate all of the hosts instantiated by Apache Metron when not in use to avoid unnecessary charges.

Getting Started
---------------

### Prerequisites

The host used to deploy Apache Metron will need the following software tools installed.  The following versions are known to work as of the time of this writing, but by no means are these the only working versions.

  - Ansible 2.0.0.2 or 2.2.2.0
  - Python 2.7.11
  - Maven 3.3.9  

Any platform that supports these tools is suitable, but the following instructions cover only macOS.  The easiest means of installing these tools on a Mac is to use the excellent [Homebrew](http://brew.sh/) project.

1. Install Homebrew by running the following command in a terminal.  Refer to the  [Homebrew](http://brew.sh/) home page for the latest installation instructions.

  ```
  /usr/bin/ruby -e "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install)"
  ```

2. With Homebrew installed, run the following command in a terminal to install all of the required tools.

  ```
  brew cask install java
  brew install maven git
  ```

3. Install Ansible by following the instructions [here](http://docs.ansible.com/ansible/intro_installation.html#latest-releases-via-pip).

4. Ensure that a public SSH key is located at `~/.ssh/id_rsa.pub`.

  ```
  $ cat ~/.ssh/id_rsa.pub
  ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQChv5GJxPjR39UJV7VY17ivbLVlxFrH7UHwh1Jsjem4d1eYiAtde5N2y65/HRNxWbhYli9ED8k0/MRP92ejewucEbrPNq5mytPqdC4IvZ98Ln2GbqTDwvlP3T7xa/wYFOpFsOmXXql8216wSrnrS4f3XK7ze34S6/VmY+lsBYnr3dzyj8sG/mexpJgFS/w83mWJV0e/ryf4Hd7P6DZ5fO+nmTXfKNK22ga4ctcnbZ+toYcPL+ODCh8598XCKVo97XjwF5OxN3vl1p1HHguo3cHB4H1OIaqX5mUt59gFIZcAXUME89PO6NUiZDd3RTstpf125nQVkQAHu2fvW96/f037 nick@localhost
  ```

  If this file does not exist, run the following command at a terminal and accept all defaults.  Only the public key, not the private key, will be uploaded to Amazon and configured on each host to enable SSH connectivity.  While it is possible to create and use an alternative key those details will not be covered.  

  ```
  ssh-keygen -t rsa
  ```

### Amazon Web Services

If you already have an Amazon Web Services account that you have used to deploy EC2 hosts, then you should be able to skip the next few steps.

1. Head over to [Amazon Web Services](http://aws.amazon.com/) and create an account.  As part of the account creation process you will need to provide a credit card to cover any charges that may apply.

2. Create a set of user credentials through [Amazon's Identity and Access Management (IAM) ](https://console.aws.amazon.com/iam/) dashboard.  On the IAM dashboard menu click "Users" and then "Create New User". Provide a name and ensure that "Generate an access key for each user" remains checked.  Download the credentials and keep them for later use.

3.  While still in [Amazon's Identity and Access Management (IAM) ](https://console.aws.amazon.com/iam/) dashboard, click on the user that was previously created.  Click the "Permissions" tab and then the "Attach Policy" button.  Attach the following policies to the user.

  - AmazonEC2FullAccess
  - AmazonVPCFullAccess

4. Apache Metron uses the [official, open source CentOS 6](https://aws.amazon.com/marketplace/pp/B00NQAYLWO) Amazon Machine Image (AMI).  If you have never used this AMI before then you will need to accept Amazon's terms and conditions.  Navigate to the [web page for this AMI](https://aws.amazon.com/marketplace/pp/B00NQAYLWO) and click the "Continue" button.  Choose the "Manual Launch" tab then click the "Accept Software Terms" button.

Having successfully created your Amazon Web Services account, hopefully you will find that the most difficult tasks are behind us.  

### Deploy Metron

1. Use the Amazon access key by exporting its values via the shell's environment.  This allows Ansible to authenticate with Amazon EC2.  For example:

  ```
  export AWS_ACCESS_KEY_ID="AKIAI6NRFEO27E5FFELQ"
  export AWS_SECRET_ACCESS_KEY="vTDydWJQnAer7OWauUS150i+9Np7hfCXrrVVP6ed"
  ```

  Notice: You must replace the access key values above with values from your own access key.

2. Start the Apache Metron deployment process.  When prompted provide a unique name for your Metron environment or accept the default.  

  ```
  $ ./run.sh
  Metron Environment [metron-test]: my-metron-env
  ...
  ```

  The process is likely to take between 70-90 minutes.  Fortunately, everything is fully automated and you should feel free to grab a coffee.

### Explore Metron

1. After the deployment has completed successfully, a message like the following will be displayed.  Navigate to the specified resources to explore your newly minted Apache Metron environment.

  ```
  TASK [debug] *******************************************************************
  ok: [localhost] => {
      "Success": [
          "Apache Metron deployed successfully",
          "   Metron  @  http://ec2-52-37-255-142.us-west-2.compute.amazonaws.com:5000",
          "   Ambari  @  http://ec2-52-37-225-202.us-west-2.compute.amazonaws.com:8080",
          "   Sensors @  ec2-52-37-225-202.us-west-2.compute.amazonaws.com on tap0",
          "For additional information, see https://metron.apache.org/'"
      ]
  }
  ```

2. Each of the provisioned hosts will be accessible from the internet. Connecting to one over SSH as the user `centos` will not require a password as it will authenticate with the pre-defined SSH key.  

  ```
  ssh centos@ec2-52-91-215-174.compute-1.amazonaws.com
  ```

Advanced Usage
--------------

### Multiple Environments

This process can support provisioning of multiple, isolated environments.  Simply change the `env` settings in `conf/defaults.yml`.  For example, you might provision separate development, test, and production environments.

```
env: metron-test
```

### Selective Provisioning

To provision only subsets of the entire Metron deployment, Ansible tags can be specified.  For example, to only deploy the sensors on an Amazon EC2 environment, run the following command.

```
ansible-playbook -i ec2.py playbook.yml --tags "ec2,sensors"
```

### Custom SSH Key


By default, the playbook will attempt to register your public SSH key `~/.ssh/id_rsa.pub` with each provisioned host.  This enables Ansible to communicate with each host using an SSH connection.  If would prefer to use another key simply add the path to the public key file to the `key_file` property in `conf/defaults.yml`.

For example, generate a new SSH key for Metron that will be stored at `~/.ssh/my-metron-key`.

```
$ ssh-keygen -q -f ~/.ssh/my-metron-key
Enter passphrase (empty for no passphrase):
Enter same passphrase again:
```

Add the path to the newly created SSH public key to `conf/defaults.yml`.

```
key_file: ~/.ssh/metron-private-key.pub
```

Common Errors
-------------

### Error: [unsupported_operation_exception] custom format isn't supported

This error might be seen within Metron's default dashboard in Kibana 4.  This occurs when the index templates do not exist for the Snort, Bro or YAF indices in Elasticsearch.  

The dashboard expects fields to be of a certain type.  If the index templates have not been loaded correctly, the data types for the fields in these indices will be incorrect and the dashboard will display this error.

#### Solution

If you see this error, please report your findings by creating a JIRA or dropping an email to the Metron Users mailing list.  Follow these steps to work around the problem.

(1) Define which Elasticsearch host to interact with.  Any Elasticsearch host should work.
```
export ES_HOST="http://ec2-52-25-237-20.us-west-2.compute.amazonaws.com:9200"
```

(2) Confirm the index templates are in fact missing.  
```
curl -s -XGET $ES_HOST/_template
```

(3) Manually load the index templates.
```
cd metron-deployment
curl -s -XPOST $ES_HOST/_template/bro_index -d @roles/metron_elasticsearch_templates/files/es_templates/bro_index.template
curl -s -XPOST $ES_HOST/_template/snort_index -d @roles/metron_elasticsearch_templates/files/es_templates/snort_index.template
curl -s -XPOST $ES_HOST/_template/yaf_index -d @roles/metron_elasticsearch_templates/files/es_templates/yaf_index.template
```

(4) Delete the existing indexes.  Only a new index will use the templates defined in the previous step.

```
curl -s -XDELETE "$ES_HOST/yaf_index*"
curl -s -XDELETE "$ES_HOST/bro_index*"
curl -s -XDELETE "$ES_HOST/snort_index*"
```

(5) Open up Kibana and wait for the new indexes to be created.  The dashboard should now work.

### Error: 'No handler was ready to authenticate...Check your credentials'

```
TASK [Define keypair] **********************************************************
failed: [localhost] => (item=ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDXbcb1AlWsEPP
  r9jEFrn0yun3PYNidJ/...david@hasselhoff.com) => {"failed": true, "item": "ssh-r
  sa AAAAB3NzaC1yc2EAAAADAQABAAABAQDXbcb1AlWsEPPr9jEFr... david@hasselhoff.com",
  "msg": "No handler was ready to authenticate. 1 handlers were checked.
  ['HmacAuthV4Handler'] Check your credentials"}
```

#### Solution 1

This occurs when Ansible does not have the correct AWS access keys.  The following commands must return a valid access key that is defined within Amazon's [Identity and Access Management](https://console.aws.amazon.com/iam/) console.  

```
$ echo $AWS_ACCESS_KEY_ID
AKIAI6NRFEO27E5FFELQ

$ echo $AWS_SECRET_ACCESS_KEY
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
```

#### Solution 2

This error can occur if you have exported the correct AWS access key, but you are using `sudo` to run the Ansible playbook.  Do not use the `sudo` command when running the Ansible playbook.

### Error: 'OptInRequired: ... you need to accept terms and subscribe'

```
TASK [metron-test: Instantiate 1 host(s) as sensors,ambari_master,metron,ec2] **
fatal: [localhost]: FAILED! => {"changed": false, "failed": true, "msg":
"Instance creation failed => OptInRequired: In order to use this AWS Marketplace
product you need to accept terms and subscribe. To do so please visit
http://aws.amazon.com/marketplace/pp?sku=6x5jmcajty9edm3f211pqjfn2"}
to retry, use: --limit @playbook.retry
```

#### Solution

Apache Metron uses the [official CentOS 6 Amazon Machine Image](https://aws.amazon.com/marketplace/pp?sku=6x5jmcajty9edm3f211pqjfn2) when provisioning hosts. Amazon requires that you accept certain terms and conditions when using any Amazon Machine Image (AMI).  Follow the link provided in the error message to accept the terms and conditions then re-run the playbook.  

### Error: 'PendingVerification: Your account is currently being verified'

```
TASK [metron-test: Instantiate 1 host(s) as sensors,ambari_master,metron,ec2] **
fatal: [localhost]: FAILED! => {"changed": false, "failed": true, "msg":
"Instance creation failed => PendingVerification: Your account is currently
being verified. Verification normally takes less than 2 hours. Until your
account is verified, you may not be able to launch additional instances or
create additional volumes. If you are still receiving this message after more
than 2 hours, please let us know by writing to aws-verification@amazon.com. We
appreciate your patience."}
to retry, use: --limit @playbook.retry
```

#### Solution

This will occur if you are attempting to deploy Apache Metron using a newly created Amazon Web Services account.  Follow the advice of the message and wait until Amazon's verification process is complete.  Amazon has some additional [advice for dealing with this error and more](http://docs.aws.amazon.com/AWSEC2/latest/APIReference/errors-overview.html).

> Your account is pending verification. Until the verification process is complete, you may not be able to carry out requests with this account. If you have questions, contact [AWS Support](http://console.aws.amazon.com/support/home#/).

### Error: 'Instance creation failed => InstanceLimitExceeded'

```
TASK [metron-test: Instantiate 3 host(s) as search,metron,ec2] *****************
fatal: [localhost]: FAILED! => {"changed": false, "failed": true, "msg":
"Instance creation failed => InstanceLimitExceeded: You have requested more
instances (11) than your current instance limit of 10 allows for the specified
instance type. Please visit http://aws.amazon.com/contact-us/ec2-request to
request an adjustment to this limit."}
to retry, use: --limit @playbook.retry
```

#### Solution

This will occur if Apache Metron attempts to deploy more host instances than allowed by your account.  The total number of instances required for Apache Metron can be reduced by editing `deployment/amazon-ec/playbook.yml`.  Perhaps a better alternative is to request of Amazon that this limit be increased.  Amazon has some additional [advice for dealing with this error and more](http://docs.aws.amazon.com/AWSEC2/latest/APIReference/errors-overview.html).

> You've reached the limit on the number of instances you can run concurrently. The limit depends on the instance type. For more information, see [How many instances can I run in Amazon EC2](http://aws.amazon.com/ec2/faqs/#How_many_instances_can_I_run_in_Amazon_EC2). If you need additional instances, complete the [Amazon EC2 Instance Request Form](https://console.aws.amazon.com/support/home#/case/create?issueType=service-limit-increase&amp;limitType=service-code-ec2-instances).

### Error: 'SSH encountered an unknown error during the connection'

```
TASK [setup] *******************************************************************
fatal: [ec2-52-26-113-221.us-west-2.compute.amazonaws.com]: UNREACHABLE! => {
  "changed": false, "msg": "SSH encountered an unknown error during the
  connection. We recommend you re-run the command using -vvvv, which will enable
  SSH debugging output to help diagnose the issue", "unreachable": true}
```

#### Solution

This most often indicates that Ansible cannot connect to the host with the SSH key that it has access to.  This could occur if hosts are provisioned with one SSH key, but the playbook is executed subsequently with a different SSH key.  The issue can be addressed by either altering the `key_file` variable to point to the key that was used to provision the hosts or by simply terminating all hosts and re-running the playbook.
