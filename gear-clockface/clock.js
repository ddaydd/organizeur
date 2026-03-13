(function () {
    var DAYS = ['Dim', 'Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam'];
    var MONTHS = ['Jan', 'Fev', 'Mar', 'Avr', 'Mai', 'Jun',
                  'Jul', 'Aou', 'Sep', 'Oct', 'Nov', 'Dec'];

    var elHours = document.getElementById('hours');
    var elMinutes = document.getElementById('minutes');
    var elSeconds = document.getElementById('seconds');
    var elColon = document.getElementById('colon');
    var elDate = document.getElementById('date-line');
    var elBattery = document.getElementById('battery');
    var elSecondsLine = document.getElementById('seconds-line');
    var elInfoLine = document.getElementById('info-line');
    var elSapStatus = document.getElementById('sap-status');

    // Debug indicator
    function sapLog(msg) {
        if (elSapStatus) elSapStatus.textContent = msg;
    }

    // Load settings: localStorage > build-time injection > defaults
    var S = {};
    try {
        var stored = localStorage.getItem('clockSettings');
        if (stored) S = JSON.parse(stored);
    } catch (e) {}
    // Merge with build-time settings (if any)
    if (window.CLOCK_SETTINGS) {
        for (var k in window.CLOCK_SETTINGS) {
            if (!(k in S)) S[k] = window.CLOCK_SETTINGS[k];
        }
    }

    // Position an element at (x, y) using CSS left/top (centered via transform)
    function positionElement(el, x, y) {
        el.style.left = x + 'px';
        el.style.top = y + 'px';
    }

    // Apply dynamic styles
    function applySettings() {
        document.body.style.backgroundColor = S.bgColor || '#000';
        if (S.bgImage) {
            document.body.style.backgroundImage = 'url(' + S.bgImage + ')';
            document.body.style.backgroundSize = 'cover';
            document.body.style.backgroundPosition = 'center';
        } else {
            document.body.style.backgroundImage = 'none';
        }
        var timeLine = document.getElementById('time-line');
        timeLine.style.color = S.timeColor || '#fff';
        timeLine.style.fontSize = (S.timeSize || 86) + 'px';
        elDate.style.color = S.dateColor || '#aaa';
        elDate.style.fontSize = (S.dateSize || 20) + 'px';
        elSecondsLine.style.color = S.secondsColor || '#4fc3f7';
        elSecondsLine.style.display = (S.secondsShow === false) ? 'none' : '';
        elBattery.style.display = (S.batteryShow === false) ? 'none' : '';
        if (S.batteryColor) elBattery.style.color = S.batteryColor;

        // Font weight (applies to all text elements)
        var fw = S.fontWeight || 200;
        timeLine.style.fontWeight = fw;
        elDate.style.fontWeight = fw;
        elSecondsLine.style.fontWeight = fw;
        elInfoLine.style.fontWeight = fw;

        // Position elements (defaults approximate centered layout)
        positionElement(elDate, S.datePosX != null ? S.datePosX : 160, S.datePosY != null ? S.datePosY : 100);
        positionElement(timeLine, S.timePosX != null ? S.timePosX : 160, S.timePosY != null ? S.timePosY : 155);
        positionElement(elSecondsLine, S.secPosX != null ? S.secPosX : 160, S.secPosY != null ? S.secPosY : 200);
        positionElement(elInfoLine, S.infoPosX != null ? S.infoPosX : 160, S.infoPosY != null ? S.infoPosY : 240);
    }

    function pad(n) {
        return n < 10 ? '0' + n : '' + n;
    }

    function updateClock() {
        var now = new Date();
        elHours.textContent = pad(now.getHours());
        elMinutes.textContent = pad(now.getMinutes());
        elSeconds.textContent = pad(now.getSeconds());
        elDate.textContent = DAYS[now.getDay()] + ' ' + now.getDate() + ' ' + MONTHS[now.getMonth()];

        if (S.colonBlink !== false) {
            elColon.style.opacity = now.getSeconds() % 2 === 0 ? '0.6' : '0.2';
        }
    }

    function updateBattery() {
        if (S.batteryShow === false) return;
        try {
            if (typeof tizen !== 'undefined' && tizen.systeminfo) {
                tizen.systeminfo.getPropertyValue('BATTERY', function (battery) {
                    var pct = Math.round(battery.level * 100);
                    elBattery.textContent = pct + '%';
                    if (!S.batteryColor) {
                        if (pct <= 15) {
                            elBattery.style.color = '#ef5350';
                        } else if (pct <= 30) {
                            elBattery.style.color = '#ffa726';
                        } else {
                            elBattery.style.color = '#66bb6a';
                        }
                    }
                }, function () {
                    elBattery.textContent = '--';
                });
            } else {
                elBattery.textContent = '--';
            }
        } catch (e) {
            elBattery.textContent = '--';
        }
    }

    // --- SAP Consumer: receive settings from phone ---
    var SAAgent = null;
    var SASocket = null;
    var CHANNEL_ID = 200;

    function setupSocket(socket) {
        SASocket = socket;
        sapLog('SAP:ok');
        // Send a ping to verify the channel works
        try {
            SASocket.sendData(CHANNEL_ID, '{"ping":true}');
            sapLog('SAP:ok+tx');
        } catch (e) {
            sapLog('SAP:ok!tx:' + (e.message || e));
        }
        SASocket.setDataReceiveListener(function (channelId, data) {
            try {
                var msg = JSON.parse(data);

                if ('_bgC' in msg) {
                    // Background image chunk
                    if (msg._bgC === 0) {
                        window._bgChunks = [];
                    }
                    window._bgChunks = window._bgChunks || [];
                    window._bgChunks[msg._bgC] = msg._bgD;
                    sapLog('bg:' + (msg._bgC + 1) + '/' + msg._bgT);

                    // Check if all chunks received
                    if (window._bgChunks.length === msg._bgT &&
                        window._bgChunks.every(function(c) { return !!c; })) {
                        S.bgImage = window._bgChunks.join('');
                        window._bgChunks = null;
                        localStorage.setItem('clockSettings', JSON.stringify(S));
                        applySettings();
                        sapLog('bg:ok');
                    }
                } else {
                    // Normal settings
                    for (var k in msg) {
                        S[k] = msg[k];
                    }
                    localStorage.setItem('clockSettings', JSON.stringify(S));
                    applySettings();
                    sapLog('OK:' + Object.keys(msg).join(','));
                }
            } catch (e) {
                sapLog('ERR:' + (e.message || e));
            }
        });
        SASocket.setSocketStatusListener(function (reason) {
            SASocket = null;
            sapLog('SAP:disc');
        });
    }

    function initSAP() {
        // Check webapis availability
        if (typeof webapis === 'undefined') {
            sapLog('no webapis');
            return;
        }
        if (!webapis.sa) {
            sapLog('no sa');
            return;
        }
        sapLog('SAP:init');
        try {
            webapis.sa.requestSAAgent(function (agents) {
                if (agents.length > 0) {
                    SAAgent = agents[0];
                    sapLog('SAP:agent');

                    // Set listener for incoming connections from provider (phone)
                    SAAgent.setServiceConnectionListener({
                        onrequest: function (peerAgent) {
                            sapLog('SAP:req');
                            if (SAAgent) SAAgent.acceptServiceConnectionRequest(peerAgent);
                        },
                        onconnect: function (socket) {
                            setupSocket(socket);
                        },
                        onerror: function () {
                            sapLog('SAP:err');
                        }
                    });

                    // Also actively search for the phone provider
                    SAAgent.setPeerAgentFindListener({
                        onpeeragentfound: function (peerAgent) {
                            sapLog('SAP:peer');
                            if (peerAgent && SAAgent) {
                                SAAgent.requestServiceConnection(peerAgent);
                            }
                        },
                        onerror: function () {
                            sapLog('SAP:nopeer');
                        }
                    });
                    SAAgent.findPeerAgents();
                } else {
                    sapLog('SAP:0agent');
                }
            }, function (err) {
                sapLog('SAP:' + (err.name || 'err'));
            });
        } catch (e) {
            sapLog('ex:' + (e.name || '') + ' ' + (e.message || e));
        }
    }

    // Retry SAP connection periodically if not connected
    function tryReconnect() {
        if (!SASocket && SAAgent) {
            sapLog('SAP:retry');
            SAAgent.findPeerAgents();
        }
    }

    // Initialize
    applySettings();
    updateClock();
    updateBattery();
    setInterval(updateClock, 1000);
    setInterval(updateBattery, 60000);

    // Start SAP after a short delay (let clock render first)
    sapLog('wait');
    setTimeout(initSAP, 2000);
    // Retry every 10s if not connected
    setInterval(tryReconnect, 10000);
})();
