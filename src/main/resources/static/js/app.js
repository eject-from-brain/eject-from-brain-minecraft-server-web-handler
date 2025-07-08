document.addEventListener('DOMContentLoaded', function() {
    const consoleElement = document.getElementById('console');
    const startStopBtn = document.getElementById('startStopBtn');
    const restartBtn = document.getElementById('restartBtn');
    const statsBtn = document.getElementById('statsBtn');
    const clearConsoleBtn = document.getElementById('clearConsoleBtn');
    const serverCommandInput = document.getElementById('serverCommandInput');
    const sendCommandBtn = document.getElementById('sendCommandBtn');
    const serverCommand = document.getElementById('serverCommand');
    const pollInterval = document.getElementById('pollInterval');
    const applyIntervalBtn = document.getElementById('applyIntervalBtn');
    const botToken = document.getElementById('botToken');
    const chatId = document.getElementById('chatId');
    const testTelegramBtn = document.getElementById('testTelegramBtn');

    let isServerRunning = false;
    let stompClient = null;

    // WebSocket connection
    function connect() {
        const socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        stompClient.connect({}, function(frame) {
            console.log('Connected: ' + frame);
            stompClient.subscribe('/topic/console', function(message) {
                appendToConsole(message.body);
            });
        });
    }

    function checkServerStatus() {
        fetch('/api/server/status')
            .then(response => response.json())
            .then(status => {
                isServerRunning = status;
                updateUI();
            });
    }

    function appendToConsole(text) {
        consoleElement.innerHTML += text + '\n';
        consoleElement.scrollTop = consoleElement.scrollHeight;
    }

    function updateUI() {
        startStopBtn.textContent = isServerRunning ? 'Stop' : 'Start';
        restartBtn.disabled = !isServerRunning;
        statsBtn.disabled = !isServerRunning;
        serverCommandInput.disabled = !isServerRunning;
        sendCommandBtn.disabled = !isServerRunning;
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
            const command = serverCommand.value;
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
        fetch('/api/server/restart', { method: 'POST' })
            .then(response => response.text())
            .then(message => appendToConsole(message));
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
        consoleElement.innerHTML = '';
        appendToConsole('--- Console cleared ---');
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

    applyIntervalBtn.addEventListener('click', function() {
        const interval = pollInterval.value;
        appendToConsole(`Poll interval set to ${interval} hours`);
        // Here you would typically send this to the backend
    });

    testTelegramBtn.addEventListener('click', function() {
        const token = botToken.value;
        const id = chatId.value;

        fetch('/api/server/telegram/test?token=' + encodeURIComponent(token) +
            '&chatId=' + encodeURIComponent(id), { method: 'POST' })
            .then(response => response.text())
            .then(message => {
                appendToConsole('Telegram: ' + message);
            });
    });

    // Initialize
    connect();
    checkServerStatus();
    updateUI();
});