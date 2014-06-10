
var wem = {

    registerCallbacks : function (onLoadCallback, onUpdateCallback) {
        if (window.digitalData) {
            if (window.digitalData.loaded) {
                console.log("digitalData object loaded, calling on load callback immediately...");
                if (onLoadCallback) {
                    onLoadCallback(window.digitalData);
                }
            } else {
                console.log("digitalData object present but not loaded, registering callback...");
                if (onLoadCallback) {
                    window.digitalData.loadCallbacks = window.digitalData.loadCallbacks || [];
                    window.digitalData.loadCallbacks.push(onLoadCallback);
                }
                if (onUpdateCallback) {
                    window.digitalData.updateCallbacks = window.digitalData.updateCallbacks || [];
                    window.digitalData.updateCallbacks.push(onUpdateCallback);
                }
            }
        } else {
            console.log("No digital data object found, creating and registering callbacks...");
            window.digitalData = {};
            if (onLoadCallback) {
                window.digitalData.loadCallbacks = [];
                window.digitalData.loadCallbacks.push(onLoadCallback);
            }
            if (onUpdateCallback) {
                window.digitalData.updateCallbacks = [];
                window.digitalData.updateCallbacks.push(onUpdateCallback);
            }
        }
    }

}