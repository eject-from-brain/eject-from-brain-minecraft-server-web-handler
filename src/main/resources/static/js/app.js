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
    const testTelegramBtn = document.getElementById('testTelegramBtn');

    function initialize() {
        connect();
        loadTelegramSettings();
        checkServerStatus();
        updateUI();
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

    function saveSettings() {
        const command = buildServerCommand();
        if (!command) return;

        const interval = pollInterval.value;

        fetch('/api/server/settings', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                command: command,
                pollInterval: interval
            })
        })
            .then(response => {
                if (!response.ok) throw new Error('Error saving settings');
                return response.text();
            })
            .then(message => appendToConsole(message))
            .catch(error => appendToConsole(error.message));
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

    applySettingsBtn.addEventListener('click', saveSettings);

    initialize()
});