console.log("Browser home content extension loaded");

const homeExtPort = browser.runtime.connectNative("tvbrowser");
function postMessageToHomePagePort(action, data) {
    //console.log("Sending message to native app: " + action);
    homeExtPort.postMessage({ action: action, data: data });
}

let BrowserApp = {
    startVoiceSearch: function () {
        postMessageToHomePagePort("startVoiceSearch");
    },
    setSearchEngine: function (engine, customSearchEngineURL) {
        postMessageToHomePagePort("setSearchEngine", { engine: engine, customSearchEngineURL: customSearchEngineURL });
    },
    onEditBookmark: function (bookmark) {
        postMessageToHomePagePort("onEditBookmark", bookmark);
    },
    onHomePageLoaded: function () {
        postMessageToHomePagePort("onHomePageLoaded");
    },
    requestFavicon: function (url) {
        postMessageToHomePagePort("requestFavicon", url);
    },
    markBookmarkRecommendationAsUseful: function (bookmarkIndex) {
        postMessageToHomePagePort("markBookmarkRecommendationAsUseful", bookmarkIndex);
    }
}
window.wrappedJSObject.BrowserApp = cloneInto(
    BrowserApp,
    window,
    { cloneFunctions: true });

homeExtPort.onMessage.addListener(message => {
    switch (message.action) {
        case "favicon": {
            let favicon = message.data;
            if (window.wrappedJSObject.onFaviconLoaded) {
                window.wrappedJSObject.onFaviconLoaded(favicon.url, favicon.data);
            }
        }
    }
});