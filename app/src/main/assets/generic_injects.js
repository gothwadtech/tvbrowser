// download blobs support
if (!window.tvBroClicksListener) {
    window.tvBroClicksListener = function(e) {
        if (e.target && e.target.tagName && e.target.tagName.toUpperCase() == "A" && e.target.attributes && e.target.attributes.href && e.target.attributes.href.value && e.target.attributes.href.value.toLowerCase().startsWith("blob:")) {
            var fileName = e.target.download;
            var url = e.target.attributes.href.value;
            var xhr = new XMLHttpRequest();
            xhr.open('GET', url, true);
            xhr.responseType = 'blob';
            xhr.onload = function(e) {
                if (this.status == 200) {
                    var blob = this.response;
                    var reader = new FileReader();
                    reader.readAsDataURL(blob);
                    reader.onloadend = function() {
                        var base64data = reader.result;
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
    if (e.touches && e.touches.length > 0) {
        window.TVBRO_activeElement = e.target;
        window.TVBRO_touchStartX = e.touches[0].clientX;
        window.TVBRO_touchStartY = e.touches[0].clientY;
    }
});

// Android TV / STB Physical Mouse & Remote Precision Click Compatibility Engine
(function() {
    if (window.__tvHardwareMouseFixApplied) return;
    window.__tvHardwareMouseFixApplied = true;

    function findInteractiveAncestor(el) {
        if (!el || el === document.body || el === document.documentElement) return null;
        var cur = el;
        while (cur && cur !== document.body && cur !== document.documentElement) {
            var tag = cur.tagName ? cur.tagName.toUpperCase() : "";
            if (tag === 'A' || tag === 'BUTTON' || tag === 'INPUT' || tag === 'SELECT' || tag === 'TEXTAREA' || tag === 'LABEL') {
                return cur;
            }
            if (cur.getAttribute) {
                var role = cur.getAttribute('role');
                if (role === 'button' || role === 'link' || role === 'menuitem' || role === 'tab' || role === 'checkbox' || role === 'switch') {
                    return cur;
                }
                if (cur.hasAttribute('onclick') || cur.hasAttribute('jsaction') || cur.hasAttribute('data-action') || cur.hasAttribute('aria-haspopup')) {
                    return cur;
                }
                var ariaLabel = (cur.getAttribute('aria-label') || "").toLowerCase();
                if (ariaLabel.indexOf('google apps') !== -1 || ariaLabel.indexOf('sign in') !== -1 || ariaLabel.indexOf('sign up') !== -1 || ariaLabel.indexOf('menu') !== -1) {
                    return cur;
                }
                if (cur.classList && (cur.classList.contains('gb_d') || cur.classList.contains('gb_wa') || cur.classList.contains('gb_A') || cur.classList.contains('gb_B'))) {
                    return cur;
                }
            }
            cur = cur.parentElement;
        }
        return null;
    }

    var lastPointerDownInfo = null;

    document.addEventListener("pointerdown", function(e) {
        lastPointerDownInfo = {
            x: e.clientX,
            y: e.clientY,
            time: Date.now(),
            target: e.target
        };
    }, true);

    document.addEventListener("touchstart", function(e) {
        if (e.touches && e.touches.length > 0) {
            lastPointerDownInfo = {
                x: e.touches[0].clientX,
                y: e.touches[0].clientY,
                time: Date.now(),
                target: e.target
            };
        }
    }, true);

    var clickTriggeredRecently = false;
    document.addEventListener("click", function(e) {
        clickTriggeredRecently = true;
        setTimeout(function() { clickTriggeredRecently = false; }, 200);

        var interactive = findInteractiveAncestor(e.target);
        if (interactive) {
            if (interactive.tagName === 'INPUT' || interactive.tagName === 'TEXTAREA' || interactive.isContentEditable) {
                if (typeof interactive.focus === 'function') {
                    interactive.focus();
                }
            }
        }
    }, true);

    function handlePointerUpOrEnd(clientX, clientY, target) {
        if (!lastPointerDownInfo) return;
        var dx = Math.abs(clientX - lastPointerDownInfo.x);
        var dy = Math.abs(clientY - lastPointerDownInfo.y);
        var dt = Date.now() - lastPointerDownInfo.time;

        // If it was a clean stationary click (<= 15px movement within 800ms)
        if (dx <= 18 && dy <= 18 && dt < 800) {
            var hit = document.elementFromPoint(clientX, clientY) || target;
            var interactive = findInteractiveAncestor(hit);

            if (interactive) {
                // Focus interactive inputs
                if (interactive.tagName === 'INPUT' || interactive.tagName === 'TEXTAREA' || interactive.isContentEditable) {
                    if (typeof interactive.focus === 'function') {
                        interactive.focus();
                    }
                }

                // If native click did not dispatch (e.g. dropped by TV box driver on SVG child)
                setTimeout(function() {
                    if (!clickTriggeredRecently) {
                        try {
                            if (typeof interactive.click === 'function') {
                                interactive.click();
                            } else {
                                var evt = new MouseEvent('click', {
                                    bubbles: true,
                                    cancelable: true,
                                    view: window,
                                    clientX: clientX,
                                    clientY: clientY
                                });
                                interactive.dispatchEvent(evt);
                            }
                        } catch (err) {
                            // ignore
                        }
                    }
                }, 40);
            }
        }
    }

    document.addEventListener("pointerup", function(e) {
        handlePointerUpOrEnd(e.clientX, e.clientY, e.target);
    }, true);

    document.addEventListener("touchend", function(e) {
        if (lastPointerDownInfo) {
            handlePointerUpOrEnd(lastPointerDownInfo.x, lastPointerDownInfo.y, e.target);
        }
    }, true);
})();

// Real Desktop Mode fitting for TV Browser
(function() {
    var ua = navigator.userAgent || "";
    var isDesktop = ua.indexOf("Windows NT") !== -1 || ua.indexOf("X11; Linux x86_64") !== -1 || ua.indexOf("Macintosh") !== -1;
    if (isDesktop) {
        var metas = document.querySelectorAll('meta[name="viewport"]');
        for (var i = 0; i < metas.length; i++) {
            metas[i].parentNode.removeChild(metas[i]);
        }
    }
})();
