//download blobs support
if (!window.tvBroClicksListener) {
    window.tvBroClicksListener = function(e) {
        if (e.target.tagName.toUpperCase() == "A" && e.target.attributes.href.value.toLowerCase().startsWith("blob:")) {
            var fileName = e.target.download;
            var url = e.target.attributes.href.value;
            var xhr=new XMLHttpRequest();
            xhr.open('GET', e.target.attributes.href.value, true);
            xhr.responseType = 'blob';
            xhr.onload = function(e) {
                if (this.status == 200) {
                    var blob = this.response;
                    var reader = new FileReader();
                    reader.readAsDataURL(blob);
                    reader.onloadend = function() {
                        base64data = reader.result;
                        BrowserApp.takeBlobDownloadData(base64data, fileName, url, blob.type);
                    }
                }
            };
            xhr.send();
            e.stopPropagation();
            e.preventDefault();
        }
    };
    document.addEventListener("click", window.tvBroClicksListener);
}

// video playback control support
if (!('playing' in HTMLMediaElement.prototype)) {
    try {
        Object.defineProperty(HTMLMediaElement.prototype, 'playing', {
            get: function() {
                return !!(this.currentTime > 0 && !this.paused && !this.ended && this.readyState > 2);
            },
            configurable: true,
            enumerable: false
        });
    } catch (e) {
        // Ignore if already defined by host page or previous injection
    }
}

window.tvBroTogglePlayback = function() {
  var media = document.querySelector('video') || document.querySelector('audio');
  if (media) {
      if (media.playing) {
        media.pause();
      } else {
        media.play();
      }
  }
}

window.tvBroStopPlayback = function() {
  var media = document.querySelector('video') || document.querySelector('audio');
  if (media) {
      media.pause();
      media.currentTime = 0;
  }
}

window.tvBroRewind = function() {
    var media = document.querySelector('video') || document.querySelector('audio');
    if (media) {
        media.currentTime -= 10;
    }
}

window.tvBroFastForward = function() {
    var media = document.querySelector('video') || document.querySelector('audio');
    if (media) {
        media.currentTime += 10;
    }
}

// context menu support
window.addEventListener("touchstart", function(e) {
    window.TVBRO_activeElement = e.target;
    window.TVBRO_touchStartX = e.touches[0].clientX;
    window.TVBRO_touchStartY = e.touches[0].clientY;
});

// Android TV / STB Physical Mouse Click & Pointer Fix
(function() {
    if (window.__tvHardwareMouseFixApplied) return;
    window.__tvHardwareMouseFixApplied = true;

    // Fix elementFromPoint clicks when pointer-events or hover state doesn't trigger natural click on STBs
    document.addEventListener("pointerup", function(e) {
        if (e.pointerType === "mouse" && e.button === 0) {
            var target = document.elementFromPoint(e.clientX, e.clientY);
            if (target && !e.defaultPrevented) {
                // Trigger focus for inputs if missed
                if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable) {
                    target.focus();
                }
            }
        }
    }, { passive: true });
})();

// Real Desktop Mode fitting for TV Browser
(function() {
    var ua = navigator.userAgent || "";
    var isDesktop = ua.indexOf("Windows NT") !== -1 || ua.indexOf("X11; Linux x86_64") !== -1 || ua.indexOf("Macintosh") !== -1;
    if (isDesktop) {
        // Remove restrictive mobile viewport tags so WebView's overview mode can scale the full desktop width into the screen
        var metas = document.querySelectorAll('meta[name="viewport"]');
        for (var i = 0; i < metas.length; i++) {
            metas[i].parentNode.removeChild(metas[i]);
        }
    }
})();