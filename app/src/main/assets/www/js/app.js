"use strict";

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
  const cpu = randomBetween(12, 78);
  const ramUsed = randomBetween(2.1, 6.5).toFixed(1);
  const diskUsed = randomBetween(12, 48).toFixed(0);

  return Promise.resolve({
    cpu,
    ram: {
      used: ramUsed,
      total: "8.0",
    },
    disk: {
      used: diskUsed,
      total: "64",
    },
  });
}

function fetchFilesList() {
  return Promise.resolve([
    { name: "Downloads", type: "Folder", size: "-", modified: "Today 14:12" },
    { name: "Projects", type: "Folder", size: "-", modified: "Today 11:04" },
    { name: "report.pdf", type: "File", size: "2.4 MB", modified: "Yesterday 18:33" },
    { name: "notes.txt", type: "File", size: "14 KB", modified: "Yesterday 09:21" },
  ]);
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
