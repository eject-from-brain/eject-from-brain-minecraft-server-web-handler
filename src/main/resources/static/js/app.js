document.addEventListener('DOMContentLoaded', function() {
    const backupDir = document.getElementById('backupDir');
    const maxBackups = document.getElementById('maxBackups');
    const backupTime = document.getElementById('backupTime');
    const backupInterval = document.getElementById('backupInterval');
    const autoBackup = document.getElementById('autoBackup');
    const createBackupBtn = document.getElementById('createBackupBtn');
    const restoreBackupBtn = document.getElementById('restoreBackupBtn');
    const deleteBackupBtn = document.getElementById('deleteBackupBtn');
    const backupList = document.getElementById('backupList');
    const saveBackupSettingsBtn = document.getElementById('saveBackupSettingsBtn');
    const browseBackupDirBtn = document.getElementById('browseBackupDirBtn');
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
    const style = document.createElement('style');
    document.getElementById('saveConfigBtnFromTg').addEventListener('click', saveAllSettings);
    document.getElementById('saveConfigBtnFromSettings').addEventListener('click', saveAllSettings);
    document.getElementById('autoRun').checked = autoRun;
    document.getElementById('notificationTimes').addEventListener('blur', function() {
        const times = this.value.split(',');
        const validFormat = /^(\d+[hm])$/i;

        for (let time of times) {
            time = time.trim();
            if (!validFormat.test(time)) {
                alert(`Invalid time format: ${time}. Please use format like "3h" or "30m"`);
                this.focus();
                return;
            }
        }
    });

    function initialize() {
        connect();
        loadTelegramSettings();
        loadSecuritySettings();
        loadDefaultSettings();
        loadBackupSettings();
        checkServerStatus();
        updateUI();
    }

    function loadBackupSettings() {
        fetch('/api/server/backup/settings')
            .then(response => {
                if (!response.ok) throw new Error('Error loading backup settings');
                return response.json();
            })
            .then(settings => {
                document.getElementById('enableRestartForBackup').checked = settings.enabled || false;
                document.getElementById('enableRestartNotifications').checked = settings.notificationsEnabled || false;
                document.getElementById('notificationTemplate').value = settings.notificationTemplate || 'Server will restart in {time} for scheduled maintenance';
                document.getElementById('notificationTimes').value = settings.notificationTimes || '3h,2h,1h,30m,15m,5m,3m,2m,1m';
                backupDir.value = settings.directory || 'backups';
                backupTime.value = settings.backupTime || '04:00';
                document.getElementById('dailyBackup').checked = settings.dailyEnabled || false;
                document.getElementById('dailyMaxBackups').value = settings.dailyMaxBackups || 1;
                document.getElementById('weeklyBackup').checked = settings.weeklyEnabled || false;
                document.getElementById('weeklyMaxBackups').value = settings.weeklyMaxBackups || 1;
                document.getElementById('monthlyBackup').checked = settings.monthlyEnabled || false;
                document.getElementById('monthlyMaxBackups').value = settings.monthlyMaxBackups || 1;

                refreshBackupLists();
            })
            .catch(error => console.log('Error loading backup settings:', error));
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

    function loadSecuritySettings() {
        fetch('/api/server/security/settings')
            .then(response => {
                if (!response.ok) throw new Error('Error loading Security settings');
                return response.json();
            })
            .then(settings => {
                if (settings.username) username.value = settings.username;
                if (settings.password) password.value = settings.password;
            })
            .catch(error => console.log('Error loading Security settings:', error));
    }

    let isServerRunning = false;
    let stompClient = null;
    let selectedBackup = null;

    function connect() {
        const socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        stompClient.connect({}, function(frame) {
            console.log('Connected: ' + frame);
            // Загружаем историю логов при подключении
            fetch('/api/server/logs')
                .then(response => response.json())
                .then(logs => {
                    consoleElement.innerHTML = '';
                    logs.forEach(log => appendToConsole(log));
                });

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

        document.body.classList.add('saving-data');

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
                return fetch('/api/server/save-config', { method: 'POST' });
            })
            .then(response => {
                if (!response.ok) throw new Error('Error saving configuration to file');
                return response.text();
            })
            .then(message => {
                appendToConsole(message);
                setTimeout(() => {
                    window.location.reload();
                }, 500);
            })
            .catch(error => {
                document.body.classList.remove('saving-data');
                appendToConsole(error.message);
                alert('Error: ' + error.message);
            });
    }

    function refreshBackupLists() {
        const backupTreeList = document.getElementById('backupTreeList');
        backupTreeList.innerHTML = '<li class="list-group-item">Loading backups...</li>';

        Promise.all([
            fetch('/api/server/backup/list/daily').then(res => res.ok ? res.json() : []),
            fetch('/api/server/backup/list/weekly').then(res => res.ok ? res.json() : []),
            fetch('/api/server/backup/list/monthly').then(res => res.ok ? res.json() : []),
            fetch('/api/server/backup/list/manual').then(res => res.ok ? res.json() : [])
        ])
            .then(([daily, weekly, monthly, manual]) => {
                backupTreeList.innerHTML = '';

                if (manual.length > 0) {
                    const manualItem = document.createElement('li');
                    manualItem.className = 'list-group-item';
                    manualItem.innerHTML = `
                <strong>Manual Backups</strong>
                <ul class="list-group mt-2" id="manualBackups"></ul>
            `;
                    backupTreeList.appendChild(manualItem);

                    const manualList = manualItem.querySelector('#manualBackups');
                    manual.forEach(backup => {
                        const li = document.createElement('li');
                        li.className = 'list-group-item backup-item';
                        li.dataset.type = 'manual';
                        li.dataset.name = backup;
                        li.textContent = backup;
                        li.addEventListener('click', function() {
                            selectBackup(this);
                        });
                        manualList.appendChild(li);
                    });
                }

                if (daily.length > 0) {
                    const dailyItem = document.createElement('li');
                    dailyItem.className = 'list-group-item';
                    dailyItem.innerHTML = `
                <strong>Daily Backups</strong>
                <ul class="list-group mt-2" id="dailyBackups"></ul>
            `;
                    backupTreeList.appendChild(dailyItem);

                    const dailyList = dailyItem.querySelector('#dailyBackups');
                    daily.forEach(backup => {
                        const li = document.createElement('li');
                        li.className = 'list-group-item backup-item';
                        li.dataset.type = 'daily';
                        li.dataset.name = backup;
                        li.textContent = backup;
                        li.addEventListener('click', function() {
                            selectBackup(this);
                        });
                        dailyList.appendChild(li);
                    });
                }

                if (weekly.length > 0) {
                    const weeklyItem = document.createElement('li');
                    weeklyItem.className = 'list-group-item';
                    weeklyItem.innerHTML = `
                <strong>Weekly Backups</strong>
                <ul class="list-group mt-2" id="weeklyBackups"></ul>
            `;
                    backupTreeList.appendChild(weeklyItem);

                    const weeklyList = weeklyItem.querySelector('#weeklyBackups');
                    weekly.forEach(backup => {
                        const li = document.createElement('li');
                        li.className = 'list-group-item backup-item';
                        li.dataset.type = 'weekly';
                        li.dataset.name = backup;
                        li.textContent = backup;
                        li.addEventListener('click', function() {
                            selectBackup(this);
                        });
                        weeklyList.appendChild(li);
                    });
                }

                if (monthly.length > 0) {
                    const monthlyItem = document.createElement('li');
                    monthlyItem.className = 'list-group-item';
                    monthlyItem.innerHTML = `
                <strong>Monthly Backups</strong>
                <ul class="list-group mt-2" id="monthlyBackups"></ul>
            `;
                    backupTreeList.appendChild(monthlyItem);

                    const monthlyList = monthlyItem.querySelector('#monthlyBackups');
                    monthly.forEach(backup => {
                        const li = document.createElement('li');
                        li.className = 'list-group-item backup-item';
                        li.dataset.type = 'monthly';
                        li.dataset.name = backup;
                        li.textContent = backup;
                        li.addEventListener('click', function() {
                            selectBackup(this);
                        });
                        monthlyList.appendChild(li);
                    });
                }

                if (backupTreeList.children.length === 0) {
                    backupTreeList.innerHTML = '<li class="list-group-item">No backups available</li>';
                }
            })
            .catch(error => {
                console.log('Error loading backups:', error);
                backupTreeList.innerHTML = '<li class="list-group-item">Error loading backups</li>';
            });
    }

    function selectBackup(element) {
        document.querySelectorAll('.backup-item').forEach(item => {
            item.classList.remove('active');
        });

        element.classList.add('active');
        selectedBackup = {
            name: element.dataset.name,
            type: element.dataset.type
        };

        document.getElementById('restoreBackupBtn').disabled = false;
        document.getElementById('deleteBackupBtn').disabled = false;
    }

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

    createBackupBtn.addEventListener('click', function() {
        fetch('/api/server/backup/create', { method: 'POST' })
            .then(response => {
                if (!response.ok) throw new Error('Error creating backup');
                return response.text();
            })
            .then(message => {
                appendToConsole(message);
                refreshBackupLists();
            })
            .catch(error => appendToConsole(error.message));
    });

    restoreBackupBtn.addEventListener('click', function() {
        if (!selectedBackup) return;

        if (confirm(`Are you sure you want to restore ${selectedBackup.type} backup ${selectedBackup.name}? This will overwrite current server files.`)) {
            fetch('/api/server/backup/restore?backupName=' + encodeURIComponent(selectedBackup.name) +
                '&type=' + encodeURIComponent(selectedBackup.type), {
                method: 'POST'
            })
                .then(response => {
                    if (!response.ok) throw new Error('Error restoring backup');
                    return response.text();
                })
                .then(message => appendToConsole(message))
                .catch(error => appendToConsole(error.message));
        }
    });

    deleteBackupBtn.addEventListener('click', function() {
        if (!selectedBackup) return;

        if (confirm(`Are you sure you want to delete ${selectedBackup.type} backup ${selectedBackup.name}? This action cannot be undone.`)) {
            fetch('/api/server/backup/delete?backupName=' + encodeURIComponent(selectedBackup.name) +
                '&type=' + encodeURIComponent(selectedBackup.type), {
                method: 'DELETE'
            })
                .then(response => {
                    if (!response.ok) throw new Error('Error deleting backup');
                    return response.text();
                })
                .then(message => {
                    appendToConsole(message);
                    refreshBackupLists();
                    selectedBackup = null;
                    document.getElementById('restoreBackupBtn').disabled = true;
                    document.getElementById('deleteBackupBtn').disabled = true;
                })
                .catch(error => appendToConsole(error.message));
        }
    });


    saveBackupSettingsBtn.addEventListener('click', function() {
        const settings = {
            enabled: document.getElementById('enableRestartForBackup').checked,
            notificationsEnabled: document.getElementById('enableRestartNotifications').checked,
            notificationTimes: document.getElementById('notificationTimes').value,
            notificationTemplate: document.getElementById('notificationTemplate').value,
            directory: backupDir.value,
            backupTime: backupTime.value,
            dailyEnabled: document.getElementById('dailyBackup').checked,
            dailyMaxBackups: document.getElementById('dailyMaxBackups').value,
            weeklyEnabled: document.getElementById('weeklyBackup').checked,
            weeklyMaxBackups: document.getElementById('weeklyMaxBackups').value,
            monthlyEnabled: document.getElementById('monthlyBackup').checked,
            monthlyMaxBackups: document.getElementById('monthlyMaxBackups').value
        };

        fetch('/api/server/backup/settings', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(settings)
        })
            .then(response => {
                if (!response.ok) throw new Error('Error saving backup settings');
                return response.text();
            })
            .then(message => {
                appendToConsole(message);
                refreshBackupLists();
            })
            .catch(error => appendToConsole(error.message));
    });

    initialize()
});