### Simple Hub Monitor

This driver implements a simple and lightweight hub monitor for tracking key hub statistics. It is intended to be used to feed a monitoring tool such as [InfluxDB-Logger](https://github.com/HubitatCommunity/InfluxDB-Logger).

Simple Hub Monitor produces the following events:

* **cpuPct**
* **freeMemory**
* **dbSize**
* **temperature**

**cpuPct** is an approximation of the percentage of CPU utilization, derived from the 1 minute load average as reported by the system. The formula used is `Load Average * 100 / Number of CPUs`. Note that since it is derived from the load average, the value may exceed 100%.

**freeMemory** is the amount of free memory in the system. The value is in MB.

**dbSize** is the size of the hub database as reported by the system. The value is in MB.

**temperature** is the temperature of the CPU. The value is in degrees Celsius or Fahrenheit as determined by the Temp Scale setting in Hub Details.

Simple Hub Monitor also provides two custom functions that may be used for automation:

**reboot** Initiate an immediate reboot of the hub.

**rebootWithRebuild** Initiate an immediate reboot of the hub and rebuild the database on startup.

**Please note that Simple Hub Monitor requires firmware version 2.4.3.127 or above**
