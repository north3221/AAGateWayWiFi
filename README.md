# AAGateWay via tasker

A modified version of [AAGateWay](https://github.com/olivluca/AAGateWay) which is modified from [the original AAGateWay](https://github.com/borconi/AAGateWay) that requires [AAstarter](https://github.com/north3221/AAstarter) on the master phone.

You can build it or get it from the [Releases](https://github.com/north3221/AAGateWay/releases) section and you have to install it on the slave phone.

I've tested it on a couple slaves with two differnt cars. I use tasker to manage the automation of this, both on the slave and master.

I have the slave rooted. Likely you dont need it rooted but will probably need to adapt a few of the actions. Tasker should tell you any missing permissions you need like write secure settings etc.

[Slave AA Gateway Taskernet](https://taskernet.com/shares/?user=AS35m8nqYTffEdExZ6ozF%2BBQfM24JDN5ykKGTNXQJ8EIFoh9D8HPiE1OzO69y84JwUDw5TlK&id=Project%3AAAGateway-tcp)

I dont have the master rooted.

[Master AA Starter Taskernet](https://taskernet.com/shares/?user=AS35m8nqYTffEdExZ6ozF%2BBQfM24JDN5ykKGTNXQJ8EIFoh9D8HPiE1OzO69y84JwUDw5TlK&id=Project%3AAndroidAuto)
NB it uses a public variable %UIMODE_TEMP, because Android 12 has broken %UIMODE in tasker, so I have created

[AA Helper Taskernet](https://taskernet.com/shares/?user=AS35m8nqYTffEdExZ6ozF%2BBQfM24JDN5ykKGTNXQJ8EIFoh9D8HPiE1OzO69y84JwUDw5TlK&id=Project%3AAA+Helper)
If you are using a phone where %UIMODE works in tasker, then you dont need this helper but you do need to change all the references from %UIMODE_TEMP to %UIMODE

To get internet access on my master when conencted to slave wifi with no internet I use two things:
	
- Mobile data only apps: Settings>Connections>Data Usage>Mobile data only apps
	
- A forward proxy: configured in wifi settings for connecting to gateway and run apache forward proxy via termux (triggered in tasker)

I combine both, so I only need to configure a few apps to have mobile data only. Termux (must be set for proxy to work) and Whatsapp (as proxy is outbound only not inbound push)

Mobile data only: There is a task 'Update Mobile Data Apps' which will write the current mobile data only apps to a variable. That variable will be used to set those apps to mobile data when you connect to slave hotspot

So just set up the apps you want and then run that task so the list is stored for future use.

I'll write up the apache forward proxy later if people interested, but tbh the mobile data works well. 

=========================


below is the original README:

# AAGateWay

A super simple app which allows the connection to Android Auto over Wifi. It requires an Android Auto compatible car in the first place.

# License

You are free to use the code for personal use in any shape or form you want, and implement any modification you wish, however you are stictly forbiden in creating and publishing app with the same or similar purposer, regardless if the app is free or comrecial. If you wish to use the code in building and releaseing your own app, please seek written approval before proceeding.

# Copyright
Emil Borconi-Szedressy (C) 2017 - Wakefield - United Kingdom

# Requirements

* Android Studio 3.4.1 or higher
* Gradle 5.1.1
* Android API 27


# Build 

```
$> ./gradlew assemble 
```

This will generate an `apk` file inside build directory `./app/build/outputs/apk/debug`

# Install in debug device

```
$> ./gradlew installDebug
```
