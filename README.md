statuses-notifier
=================

Jenkins notifier for https://github.com/innoq/statuses.

# Setup

https://wiki.jenkins-ci.org/display/JENKINS/Plugin+tutorial#Plugintutorial-SettingUpEnvironment

# Run

    mvn hpi:run
    
* go to localhost:8080
* in Jenkins Configuration do a basic setup for the Notifier (url/user/pass)
* do a connection test
* Check wether include Jenkins URL to your Post and/or only Post after Project state changes.

* Configure persons in your project to notifier (@user1, @user2)
