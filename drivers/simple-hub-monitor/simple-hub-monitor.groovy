/* groovylint-disable LineLength */

/*
 * Copyright (c) 2025, Denny Page
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Simple cpu/mem/db/temp monitor device.
 *
 * Version 1.0.0    Initial release
 */

metadata {
    definition(
        name: "Simple Hub Monitor", namespace: "cococafe", author: "Denny Page",
        importUrl: "https://raw.githubusercontent.com/dennypage/hubitat/master/drivers/simple-hub-monitor/simple-hub-monitor.groovy"
    )
    {
        capability "Initialize"
        capability "Refresh"
        capability "Actuator"

        attribute "cpuPct", "number"
        attribute "freeMemory", "number"
        attribute "dbSize", "number"
        attribute "temperature", "string"

        command "reboot"
        command "rebootWithRebuild"
    }
}

preferences {
    input(
        name: "pollFreq",
        title: "Polling Frequency",
        type: "number",
        defaultValue: 1,
        range: "1..15",
        required: true
    )
    input(
        name: "rebootEnable",
        title: "Enable Hub Reboot",
        type: "bool",
        defaultValue: false,
        description: "This option is required to enable the Reboot functions."
    )
}

void installed() {
    runIn(1, updated)
}

void uninstalled() {
    unschedule()
}

void initialize() {
    unschedule()
    runIn(15, updated)
}

void updated() {
    unschedule()
    if (!versionCheck()) {
        return
    }
    refresh()
    schedule("0 0/${pollFreq} * ? * * *", refresh)
}

void refresh() {
    cpuPoll()
    memPoll()
    dbPoll()
    tempPoll()
}

void reboot() {
    log.warn('Reboot reqeusted')
    sendHubRebootCommand(false)
}

void rebootWithRebuild()
{
    log.warn('Reboot With Rebuild reqeusted')
    sendHubRebootCommand(true)
}

private Boolean versionCheck() {
    osVersion = location.hub.firmwareVersionString.split('\\.')
    vMajor = osVersion[0].toInteger()
    vMinor = osVersion[1].toInteger()
    vPatch = osVersion[2].toInteger()
    vBuild = osVersion[3].toInteger()

    if ((vMajor < 2) ||
        (vMajor == 2 && vMinor < 4) ||
        (vMajor == 2 && vMinor == 4 && vPatch < 3) ||
        (vMajor == 2 && vMinor == 4 && vPatch < 3 && vBuild < 127)) {
        log.error("${device} requires firmware version 2.4.3.127 or above")
        return false
    }

    return true
}

private void cpuPoll() {
    def params = [
        uri: 'http://127.0.0.1:8080',
        path: '/hub/cpuInfo',
        textParser: false
    ]

    try {
        httpGet(params) { response ->
            // cpuInfo is a two line response that looks like this:
            //    Processors 4
            //    Load Average 0.34
            // Load Average is the 1 minute load average
            String text = response.data.getText()
            def matcher = text =~ /Processors (\d+)\nLoad Average ([\.0-9]+)/
            if (matcher.find()) {
                Double processors = matcher.group(1).toDouble()
                Double load = matcher.group(2).toDouble()
                Double cpu = (load * 100.0 / processors).round(1)
                sendEvent(name: 'cpuPct', unit: '%', value: cpu)
            }
            else {
                log.error('failed to decode cpu information')
            }
        }
    }
    catch (e) {
        log.warn('failed to retrieve cpu information')
    }
}

private void memPoll() {
    def params = [
        uri: 'http://127.0.0.1:8080',
        path: '/hub/advanced/freeOSMemory',
        textParser: false
    ]

    try {
        httpGet(params) { response ->
            // freeOSMemory is a simple one field response that looks like this:
            //     171456
            // The value is in MB
            Long freeMemory = response.data.toLong()
            sendEvent(name: 'freeMemory', unit: 'MB', value: freeMemory)
        }
    }
    catch (e) {
        log.warn('failed to retrieve memory information')
    }
}

private void dbPoll() {
    def params = [
        uri: 'http://127.0.0.1:8080',
        path: '/hub/advanced/databaseSize',
        textParser: false
    ]

    try {
        httpGet(params) { response ->
            // databaseSize is a simple one field response that looks like this:
            //     5
            // The value is in MB
            Long dbSize = response.data.toLong()
            sendEvent(name: 'dbSize', unit: 'MB', value: dbSize)
        }
    }
    catch (e) {
        log.warn('failed to retrieve database information')
    }
}

private void tempPoll() {
    def params = [
        uri: 'http://127.0.0.1:8080',
        path: '/hub/advanced/internalTempCelsius',
        textParser: false
    ]

    try {
        httpGet(params) { response ->
            // internalTempCelsius is a simple one field response that looks like this:
            //     41.0
            // The value is in degrees Celsius
            BigDecimal temperature = response.data.toBigDecimal()
            if (location.temperatureScale == 'F')
                sendEvent(name: 'temperature', unit: '°F', value: celsiusToFahrenheit(temperature))
            else
                sendEvent(name: 'temperature', unit: '°C', value: temperature)
        }
    }
    catch (e) {
        log.warn('failed to retrieve temperature information')
    }
}

private void sendHubRebootCommand(Boolean rebuild = false) {
    if (!rebootEnable) {
        log.error('Hub reboot is not enabled in preferences')
        return
    }

    def postParams = [
        uri: 'http://127.0.0.1:8080',
        path: '/hub/reboot'
    ]
    if (rebuild) {
        postParams['contentType'] = 'application/x-www-form-urlencoded'
        postParams['body'] = 'rebuildDatabase:"true"'
    }

    log.warn('sending hub reboot command...')
    httpPost(postParams) { response ->
        log.warn('hub reboot command sent')
    }
}
