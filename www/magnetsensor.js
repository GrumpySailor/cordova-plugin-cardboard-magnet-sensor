var argscheck = require('cordova/argscheck'),
    utils = require("cordova/utils"),
    exec = require("cordova/exec")

var running = false;

var winCallback, failCallback;


// Tells native to start.
function start() {
    exec(function(a) {
        console.log('NEW MAGNET RECEIVED')  
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
    exec(null, null, "MagnetSensor", "stop", []);
    running = false;
}

var magnetsensor = {
    onCardboardTriggerListener: function(successCallback, errorCallback) {
        winCallback = successCallback
        failCallback = errorCallback
        start();
    },
};

module.exports = magnetsensor;
