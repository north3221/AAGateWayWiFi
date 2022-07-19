# AAGateWay Master Wifi Hotspot (Root Required)

I have reworked the AAGateway to wait for USB adapter AND connection to WiFi.  
This only works running on the slave and connecting to master wifi hotspot

Shout out to the (couldn't of done this without them):  
* The original [AAGateWay](https://github.com/borconi/AAGateWay)
* Also the modified [AAGateWay](https://github.com/olivluca/AAGateWay)

[XDA forum link](https://forum.xda-developers.com/t/android-4-1-proxy-gateway-for-android-auto.3813163) for discussion, requests and support

You can build it or get it from the [Releases](https://github.com/north3221/AAGateWayWiFi/releases) section and you have to install it on the slave phone.

## Set up:
### MASTER
So you MUST have [Android Auto Head Unit Server running](https://developer.android.com/training/cars/testing#:~:text=You%20only%20need%20to%20enable,server%20(see%20figure%201).) on your Master device  
You also must have WifI tether on Master device  
NB I use Tasker to automate both of these things i.e. turn on wifi tether when connected to car bluetooth and some screen touches for AA HUS

### SLAVE
* Remove battery restrictions on AAgateway app on slave - Click the request battery button  
* Allow storage access - Click the request storage button This is for writing a log file to sdcard when logging set to full + log  
* Root is required - click request root button. This is to enable toggling USB to connect to car head unit when service ready  
* Ensure slave can connect to master wifi tether, i.e. save the network. But NO other wifi (you don't want it to connect to the wrong network)

#### Settings
* Logging Level: Prob worth changing it to full + log while setting up (shows stuff in ui and writes to sdcard/aagatewaylog.txt)  
  Full just shows it all in ui (ui only updates when app is open), and info just shows the key elements on screen.  
* The setting control wifi means the app will turn on wifi when the slave is powered and turn it off after its has no power.  
  The app only waits for wifi connection, not specifically your master, hence make sure only one wifi set up
* Alternative usb toggle is if the standard one doesn't work (see later)

First time you try connecting you will need to allowed root access (TODO add prompt at startup)  
NB it wont ask till connected to car and wifi   
First time it will also prompt do you want to use aawireless for android automotive, say 'always'   

Once done, plug slave into car, turn car on, enable wifi (if set wifi control to true, does it for you)  
Slave should show it has usb device and once connects to master wifi, then wifi will show connected, then usb will toggle and service will start

AA should fire up, if not it will retry. Let it retry a few times   
If it doesn't work, unplug slave and restart your HUS on master (i.e. stop it and start it again). Then try again

If after a few attempts of this you never get a flash of Android Auto then possibly there is issue between head unit, slave and master.   
I've put in an alternate usb toggle option. So change that setting to true and try it all again.     
If still fails after that then no idea, sorry. If you can debug it yourself great, raise a pr

NB I an NOT a developer, just a hobbyist who likes to play and would like AA wireless in his car :-)  
I only have a couple of combinations to try and test this on. Happy to look at issues but be patient I may never get to em, my focus is it working for me, just sharing to try and help others.connect  
Please don't start complaining if its not working or I am not responsive to questions or issues


#### Known issues

If slave stops, master doesn't seem to realise (AA still thinks its connected). This bawks the HUS. So you need to restart the HUS before next use  
TODO find a more graceful way of killing connection

Many other issues... this wont be plane sailing, so dont expect it to be!!