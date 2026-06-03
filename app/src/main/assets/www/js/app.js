"use strict";

let terminal = null;
let fitAddon = null;
let rfb = null;

document.addEventListener("DOMContentLoaded", () => {
  const page = document.body.dataset.page;
  if (page === "login") {
    initLogin();
  }
  if (page === "dashboard") {
    initDashboard();
  }
});

function initLogin() {
  const form = document.getElementById("loginForm");
  const message = document.getElementById("loginMessage");

  if (!form || !message) {
    return;
  }

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const formData = new FormData(form);
    const username = String(formData.get("username") || "").trim();
    const password = String(formData.get("password") || "").trim();

    if (!username || !password) {
      message.textContent = "Please enter a username and password.";
      return;
    }

    message.textContent = "Signing in...";
    localStorage.setItem("aptdesk.user", username);
    window.location.href = "dashboard.html";
  });
}

function initDashboard() {
  const refreshButton = document.getElementById("refreshBtn");
  const statsRefreshButton = document.getElementById("statsRefreshBtn");

  setupTabs();
  renderFiles();
  renderSoftware();
  renderSessions();
  updateStatus();

  if (refreshButton) {
    refreshButton.addEventListener("click", updateStatus);
  }

  if (statsRefreshButton) {
    statsRefreshButton.addEventListener("click", updateStatus);
  }

  setInterval(updateStatus, 5000);
}

function setupTabs() {
  const tabTriggers = document.querySelectorAll("[data-tab]");

  tabTriggers.forEach((trigger) => {
    trigger.addEventListener("click", () => {
      const tab = trigger.getAttribute("data-tab");
      if (tab) {
        setActiveTab(tab);
      }
    });
  });

  setActiveTab("desktop");
}

function setActiveTab(tab) {
  document.querySelectorAll(".tree-item, .tab-button").forEach((item) => {
    item.classList.toggle("active", item.getAttribute("data-tab") === tab);
  });

  document.querySelectorAll(".tab-panel").forEach((panel) => {
    panel.classList.toggle("active", panel.getAttribute("data-tab") === tab);
  });

  if (tab === "terminal") {
    initTerminal();
  } else if (tab === "desktop") {
    initDesktop();
  }
}

function initTerminal() {
  if (terminal) return; // Already initialized

  const container = document.getElementById("terminal-container");
  if (!container) return;

  terminal = new Terminal({
    cursorBlink: true,
    fontFamily: '"Fira Code", monospace',
    theme: {
      background: '#000000',
      foreground: '#ffffff'
    }
  });

  fitAddon = new FitAddon.FitAddon();
  terminal.loadAddon(fitAddon);
  terminal.open(container);
  fitAddon.fit();

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const wsUrl = `${protocol}//${window.location.host}/ws/term/`;
  
  const socket = new WebSocket(wsUrl);

  socket.onopen = () => {
    document.querySelector('#tab-terminal .status-text').textContent = "Connected to ttyd";
    document.querySelector('#tab-terminal .badge').className = "badge badge-success";
    document.querySelector('#tab-terminal .badge').textContent = "Connected";
  };

  socket.onmessage = (event) => {
    const reader = new FileReader();
    reader.onload = () => {
      terminal.write(new Uint8Array(reader.result));
    };
    if (event.data instanceof Blob) {
      reader.readAsArrayBuffer(event.data);
    } else {
      terminal.write(event.data);
    }
  };

  terminal.onData((data) => {
    if (socket.readyState === WebSocket.OPEN) {
      socket.send(data);
    }
  });

  window.addEventListener("resize", () => {
    if (document.getElementById("tab-terminal").classList.contains("active")) {
      fitAddon.fit();
    }
  });
}

function initDesktop() {
  if (rfb) return;

  const container = document.getElementById("vnc-container");
  if (!container) return;

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const wsUrl = `${protocol}//${window.location.host}/ws/vnc/`;

  rfb = new window.RFB(container, wsUrl, {
    credentials: { password: "" }
  });

  rfb.addEventListener("connect",  () => {
    document.querySelector('#tab-desktop .status-text').textContent = "Connected to XFCE";
    document.querySelector('#tab-desktop .badge').className = "badge badge-success";
    document.querySelector('#tab-desktop .badge').textContent = "Connected";
  });

  rfb.addEventListener("disconnect", (e) => {
    document.querySelector('#tab-desktop .status-text').textContent = "Disconnected: " + e.detail.reason;
    document.querySelector('#tab-desktop .badge').className = "badge badge-error";
    document.querySelector('#tab-desktop .badge').textContent = "Disconnected";
  });
}

function renderFiles() {
  const tableBody = document.getElementById("filesTable");
  if (!tableBody) {
    return;
  }

  fetchFilesList().then((files) => {
    tableBody.innerHTML = "";
    files.forEach((file) => {
      const row = document.createElement("tr");
      row.innerHTML = `
        <td>${file.name}</td>
        <td>${file.type}</td>
        <td>${file.size}</td>
        <td>${file.modified}</td>
      `;
      tableBody.appendChild(row);
    });
  });
}

function renderSoftware() {
  const tableBody = document.getElementById("softwareTable");
  if (!tableBody) {
    return;
  }

  fetchSoftwareList().then((packages) => {
    tableBody.innerHTML = "";
    packages.forEach((pkg) => {
      const row = document.createElement("tr");
      row.innerHTML = `
        <td>${pkg.name}</td>
        <td>${pkg.version}</td>
        <td class="status-cell">${pkg.status}</td>
        <td><button class="ghost-button install-button" data-package="${pkg.name}">Install</button></td>
      `;
      tableBody.appendChild(row);
    });

    tableBody.querySelectorAll(".install-button").forEach((button) => {
      button.addEventListener("click", () => {
        handleInstall(button);
      });
    });
  });
}

function renderSessions() {
  const tableBody = document.getElementById("sessionTable");
  if (!tableBody) {
    return;
  }

  fetchSessions().then((sessions) => {
    tableBody.innerHTML = "";
    sessions.forEach((session) => {
      const row = document.createElement("tr");
      row.innerHTML = `
        <td>${session.name}</td>
        <td>${session.user}</td>
        <td>${session.uptime}</td>
        <td><span class="badge badge-${session.badge}">${session.status}</span></td>
      `;
      tableBody.appendChild(row);
    });
  });
}

function updateStatus() {
  fetchStatus().then((status) => {
    setStatText("cpu", `CPU ${status.cpu}%`);
    setStatText("ram", `RAM ${status.ram.used} / ${status.ram.total} GB`);
    setStatText("disk", `Disk ${status.disk.used} / ${status.disk.total} GB`);
    setStatText("cpu-detail", `${status.cpu}%`);
    setStatText("ram-detail", `${status.ram.used} / ${status.ram.total} GB`);
    setStatText("disk-detail", `${status.disk.used} / ${status.disk.total} GB`);

    const stamp = document.getElementById("statusTimestamp");
    if (stamp) {
      stamp.textContent = `Updated ${new Date().toLocaleTimeString()}`;
    }
  });
}

function setStatText(stat, value) {
  document.querySelectorAll(`[data-stat="${stat}"]`).forEach((element) => {
    element.textContent = value;
  });
}

function handleInstall(button) {
  const pkgName = button.getAttribute("data-package");
  if (!pkgName) {
    return;
  }

  const row = button.closest("tr");
  const statusCell = row ? row.querySelector(".status-cell") : null;

  button.textContent = "Installing...";
  button.disabled = true;
  if (statusCell) {
    statusCell.textContent = "Installing";
  }

  installSoftwarePackage(pkgName).then(() => {
    if (statusCell) {
      statusCell.textContent = "Installed";
    }
    button.textContent = "Installed";
  });
}

function fetchStatus() {
  return fetch('/api/status')
    .then(response => {
        if (!response.ok) throw new Error('API error');
        return response.json();
    })
    .then(data => {
        // Return structured data for the UI
        return {
            cpu: data.status === "running" ? 15 : 0, // Mock CPU for now as API just returns status
            ram: { used: "1.2", total: "8.0" },
            disk: { used: "12", total: "64" }
        };
    })
    .catch(err => {
        return {
            cpu: 0, ram: { used: "0", total: "0" }, disk: { used: "0", total: "0" }
        };
    });
}

function fetchFilesList() {
  return fetch('/api/files/')
    .then(response => response.json())
    .then(files => {
        return files.map(file => ({
            name: file.name,
            type: file.isDirectory ? "Folder" : "File",
            size: file.isDirectory ? "-" : (file.size / 1024).toFixed(1) + " KB",
            modified: "-"
        }));
    })
    .catch(err => {
        return [];
    });
}

function fetchSoftwareList() {
  return Promise.resolve([
    { name: "Firefox", version: "126.0", status: "Available" },
    { name: "LibreOffice", version: "7.6", status: "Available" },
    { name: "Docker", version: "26.1", status: "Installed" },
    { name: "VS Code", version: "1.90", status: "Available" },
  ]);
}

function fetchSessions() {
  return Promise.resolve([
    { name: "desktop-01", user: "operator", uptime: "1h 12m", status: "Active", badge: "success" },
    { name: "terminal-02", user: "automation", uptime: "34m", status: "Idle", badge: "warning" },
    { name: "files-sync", user: "system", uptime: "2h 03m", status: "Active", badge: "success" },
  ]);
}

function installSoftwarePackage() {
  return new Promise((resolve) => {
    setTimeout(resolve, 1200);
  });
}

function randomBetween(min, max) {
  return Math.random() * (max - min) + min;
}
