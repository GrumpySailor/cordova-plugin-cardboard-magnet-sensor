var argscheck = require('cordova/argscheck'),
    utils = require("cordova/utils"),
    exec = require("cordova/exec")

var running = false;

var winCallback, failCallback;


// Tells native to start.
function start() {
    console.log('Starting MagnetSensor')
    exec(function(a) {
        if(winCallback) {
            winCallback()
        }
    }, function(e) {
        if(failCallback) {
            failCallback(e)
        }
    }, "MagnetSensor", "start", []);
    running = true;
}

// Tells native to stop.
function stop() {
    console.log('Stoping MagnetSensor')
    exec(null, null, "MagnetSensor", "stop", []);
    winCallback = null;
    failCallback = null;
    running = false;
}

var magnetsensor = {
    onCardboardTriggerListener: function(successCallback, errorCallback) {
        winCallback = successCallback
        failCallback = errorCallback
        start();
    },
    stopSensor: function() {
        stop();
    }
};

module.exports = magnetsensor;
