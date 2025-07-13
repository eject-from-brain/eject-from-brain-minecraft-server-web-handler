document.addEventListener('DOMContentLoaded', function() {
    const consoleElement = document.getElementById('console');
    const startStopBtn = document.getElementById('startStopBtn');
    const restartBtn = document.getElementById('restartBtn');
    const statsBtn = document.getElementById('statsBtn');
    const clearConsoleBtn = document.getElementById('clearConsoleBtn');
    const serverCommandInput = document.getElementById('serverCommandInput');
    const sendCommandBtn = document.getElementById('sendCommandBtn');
    const maxMemory = document.getElementById('maxMemory');
    const initMemory = document.getElementById('initMemory');
    const serverJar = document.getElementById('serverJar');
    const pollInterval = document.getElementById('pollInterval');
    const applySettingsBtn = document.getElementById('applySettingsBtn');
    const botToken = document.getElementById('botToken');
    const chatId = document.getElementById('chatId');
    const serverPort = document.getElementById('serverPort');
    const username = document.getElementById('username');
    const password = document.getElementById('password');
    const autoRun = document.getElementById('autoRun');
    const testTelegramBtn = document.getElementById('testTelegramBtn');
    document.getElementById('saveConfigBtnFromTg').addEventListener('click', saveAllSettings);
    document.getElementById('saveConfigBtnFromSettings').addEventListener('click', saveAllSettings);
    document.getElementById('autoRun').checked = autoRun;

    function initialize() {
        connect();
        loadTelegramSettings();
        loadDefaultSettings();
        checkServerStatus();
        updateUI();
    }

    function loadDefaultSettings() {
        fetch('/api/server/settings/default')
            .then(response => {
                if (!response.ok) throw new Error('Error loading default settings');
                return response.json();
            })
            .then(settings => {
                maxMemory.value = settings.xmx;
                initMemory.value = settings.xms;
                serverJar.value = settings.jar;
                pollInterval.value = settings.pollInterval;
                serverPort.value = settings.port;
                username.value = settings.username;
                username.value = settings.password;
                document.getElementById('autoRun').checked = settings.autoRun || false;
            })
            .catch(error => console.log('Error loading default settings:', error));
    }

    function loadTelegramSettings() {
        fetch('/api/server/telegram/settings')
            .then(response => {
                if (!response.ok) throw new Error('Error loading Telegram settings');
                return response.json();
            })
            .then(settings => {
                if (settings.token) botToken.value = settings.token;
                if (settings.chatId) chatId.value = settings.chatId;
            })
            .catch(error => console.log('Error loading Telegram settings:', error));
    }

    let isServerRunning = false;
    let stompClient = null;

    function connect() {
        const socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        stompClient.connect({}, function(frame) {
            console.log('Connected: ' + frame);
            stompClient.subscribe('/topic/console', function(message) {
                if (message.body === "clear") {
                    consoleElement.innerHTML = '';
                } else {
                    appendToConsole(message.body);
                }
            });
        });
    }

    function checkServerStatus() {
        fetch('/api/server/status')
            .then(response => {
                if (!response.ok) throw new Error('Error checking server status');
                return response.json();
            })
            .then(status => {
                isServerRunning = status;
                updateUI();
            })
            .catch(error => {
                appendToConsole(error.message);
                isServerRunning = false;
                updateUI();
            });
    }

    function appendToConsole(text) {
        if (text === "clear") {
            consoleElement.innerHTML = '';
        } else {
            consoleElement.innerHTML += text + '\n';
            consoleElement.scrollTop = consoleElement.scrollHeight;
        }
    }

    function updateUI() {
        startStopBtn.textContent = isServerRunning ? 'Stop' : 'Start';
        startStopBtn.className = isServerRunning ? 'btn btn-danger' : 'btn btn-success';
        restartBtn.disabled = !isServerRunning;
        statsBtn.disabled = !isServerRunning;
        serverCommandInput.disabled = !isServerRunning;
        sendCommandBtn.disabled = !isServerRunning;
    }

    function buildServerCommand() {
        const xmx = maxMemory.value;
        const xms = initMemory.value;
        const jar = serverJar.value;

        // Basic validation
        if (!jar.endsWith('.jar')) {
            appendToConsole('Error: Server jar file must end with .jar');
            return null;
        }

        if (xmx < 1 || xmx > 1024 || xms < 1 || xms > 1024) {
            appendToConsole('Error: Memory values must be between 1 and 1024');
            return null;
        }

        return `java -Xmx${xmx}G -Xms${xms}G -jar ${jar} nogui`;
    }

    function saveAllSettings() {
        const settings = {
            xmx: maxMemory.value,
            xms: initMemory.value,
            jar: serverJar.value,
            pollInterval: pollInterval.value,
            port: serverPort.value,
            autoRun: document.getElementById('autoRun').checked
        };

        const telegramSettings = {
            token: botToken.value,
            chatId: chatId.value
        };

        const securitySettings = {
            username: username.value,
            password: password.value
        };

        // Сохраняем основные настройки
        fetch('/api/server/settings', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(settings)
        })
            .then(response => {
                if (!response.ok) throw new Error('Error saving main settings');
                return response.text();
            })
            .then(() => {
                // Сохраняем Telegram настройки
                return fetch('/api/server/telegram/settings', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify(telegramSettings)
                });
            })
            .then(response => {
                if (!response.ok) throw new Error('Error saving Telegram settings');
                return response.text();
            })
            .then(() => {
                // Сохраняем настройки безопасности
                return fetch('/api/server/security/settings', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify(securitySettings)
                });
            })
            .then(response => {
                if (!response.ok) throw new Error('Error saving security settings');
                return response.text();
            })
            .then(() => {
                // Сохраняем всё в файл
                return fetch('/api/server/save-config', { method: 'POST' });
            })
            .then(response => {
                if (!response.ok) throw new Error('Error saving configuration to file');
                return response.text();
            })
            .then(message => {
                alert('All settings saved successfully!');
                appendToConsole(message);
            })
            .catch(error => {
                appendToConsole(error.message);
                alert('Error: ' + error.message);
            });
    }

    // Event listeners
    startStopBtn.addEventListener('click', function() {
        if (isServerRunning) {
            fetch('/api/server/stop', { method: 'POST' })
                .then(response => {
                    if (!response.ok) throw new Error('Error stopping server');
                    return response.text();
                })
                .then(message => {
                    appendToConsole(message);
                    isServerRunning = false;
                    updateUI();
                })
                .catch(error => appendToConsole(error.message));
        } else {
            appendToConsole("clear");
            const command = buildServerCommand();
            if (!command) return;

            fetch('/api/server/start?command=' + encodeURIComponent(command), { method: 'POST' })
                .then(response => {
                    if (!response.ok) throw new Error('Error starting server');
                    return response.text();
                })
                .then(message => {
                    appendToConsole(message);
                    isServerRunning = true;
                    updateUI();
                })
                .catch(error => appendToConsole(error.message));
        }
    });

    restartBtn.addEventListener('click', function() {
        const command = buildServerCommand();
        if (!command) return;

        fetch('/api/server/restart', { method: 'POST' })
            .then(response => {
                if (!response.ok) throw new Error('Error restarting server');
                return response.text();
            })
            .then(message => appendToConsole(message))
            .catch(error => appendToConsole(error.message));
    });

    statsBtn.addEventListener('click', function() {
        fetch('/api/server/stats')
            .then(response => response.json())
            .then(stats => {
                let statsText = `Server Stats:
                                        Players: ${stats.onlinePlayers}
                                        TPS: ${stats.tps}
                                        Memory: ${stats.memory}
                                        Uptime: ${stats.upTime}`;
                appendToConsole(statsText);
            });
    });

    clearConsoleBtn.addEventListener('click', function() {
        fetch('/api/server/clear', { method: 'POST' })
            .then(() => {
                consoleElement.innerHTML = '';
            });
    });

    serverCommandInput.addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            sendCommand();
        }
    });

    sendCommandBtn.addEventListener('click', sendCommand);

    function sendCommand() {
        const command = serverCommandInput.value;
        if (command.trim()) {
            fetch('/api/server/command?command=' + encodeURIComponent(command), { method: 'POST' })
                .then(response => response.text())
                .then(message => {
                    serverCommandInput.value = '';
                });
        }
    }

    applySettingsBtn.addEventListener('click', saveAllSettings);

    testTelegramBtn.addEventListener('click', function() {
        const token = botToken.value;
        const chatIdValue = chatId.value;

        if (!token || !chatIdValue) {
            appendToConsole('Error: Bot token and chat ID are required');
            return;
        }

        fetch('/api/server/telegram/test?token=' + encodeURIComponent(token) +
            '&chatId=' + encodeURIComponent(chatIdValue), {
            method: 'POST'
        })
            .then(response => {
                if (!response.ok) throw new Error('Connection test failed');
                return response.text();
            })
            .then(message => {
                alert(message);
                appendToConsole('Telegram connection test: ' + message);
            })
            .catch(error => {
                appendToConsole('Error testing Telegram connection: ' + error.message);
                alert('Error: ' + error.message);
            });
    });

    initialize()
});