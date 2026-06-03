"use strict";

let termLoaded = false;
let vncLoaded = false;
let filesLoaded = false;

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

  if (tab === "terminal" && !termLoaded) {
    const iframe = document.getElementById("term-iframe");
    if (iframe) {
        iframe.src = "/term/";
        termLoaded = true;
        document.querySelector('#tab-terminal .status-text').textContent = "Embedded ttyd interface";
        document.querySelector('#tab-terminal .badge').className = "badge badge-success";
        document.querySelector('#tab-terminal .badge').textContent = "Running";
    }
  } else if (tab === "desktop" && !vncLoaded) {
    const iframe = document.getElementById("vnc-iframe");
    if (iframe) {
        // autoconnect=true tells noVNC to connect immediately
        // path=vnc/ tells noVNC to connect its websocket to /vnc/ instead of the default /websockify
        iframe.src = "/vnc/vnc.html?autoconnect=true&resize=scale&path=vnc/";
        vncLoaded = true;
        document.querySelector('#tab-desktop .status-text').textContent = "Embedded noVNC interface";
        document.querySelector('#tab-desktop .badge').className = "badge badge-success";
        document.querySelector('#tab-desktop .badge').textContent = "Running";
    }
  } else if (tab === "files" && !filesLoaded) {
    const iframe = document.getElementById("files-iframe");
    if (iframe) {
        iframe.src = "/filesapp/";
        filesLoaded = true;
    }
  }
}

// Removed old mock renderFiles()

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

// Removed fetchFilesList()

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
