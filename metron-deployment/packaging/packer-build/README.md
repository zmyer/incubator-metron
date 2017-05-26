Build Metron Images
=========================

Based on the fantastic [Bento](https://github.com/chef/bento) project developed by Chef.

Images Provided
---------------------
- base-centos-6.7: Centos 6.7 + HDP. Used in the full-dev-platform Vagrant image
- quick-dev-centos-6.7: Centos 6.7 + HDP + Metron. Used for the quick-dev-platform Vagrant image.

Prerequisites
---------------------
- [Packer](https://www.packer.io/) 0.12.2
- [Virtualbox](https://www.virtualbox.org/) 5.0.16+ (Tested with 5.0.20)

Build Both Images
---------------------- 
  Navigate to \<your-project-directory\>/metron-deployment/packer-build
  Execute bin/bento build
  
  Packer will build both images and export .box files to the ./builds directory.
  
Build Single Images
---------------------- 
 Navigate to *your-project-directory*/metron-deployment/packer-build
 * Base Centos (full-dev)
 ```
bin/bento build base-centos-6.7.json
```
 * Quick Dev
 ```
bin/bento build quick-dev-centos-6.7.json
```

Using Your New Box File
---------------------- 
Modify the relevant Vagrantfile (full-dev-platform or quick-dev-platform) replacing the lines:
```
<pre><code>config.vm.box = "<i>box_name</i>"
config.ssh.insert_key = true</code></pre>
```
with
```
<pre></code>config.vm.box = "<i>test_box_name</i>"
config.vm.box = "<i>PathToBoxfile/Boxfilename</i>"
config.ssh.insert_key = true</code></pre>
```
Launch the image as usual.

Node: Vagrant will cache boxes, you can force Vagrant to reload your box by running <code>vagrant box remove <i>test_box_name</i></code> before launching your new image.

