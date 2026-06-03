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
  renderSoftware();
  renderSessions();
  updateStatus();

  if (refreshButton) {
    refreshButton.addEventListener("click", updateStatus);
  }

  if (statsRefreshButton) {
    statsRefreshButton.addEventListener("click", updateStatus);
  }

  const softwareSearchBtn = document.getElementById("softwareSearchBtn");
  const softwareListBtn = document.getElementById("softwareListBtn");
  const softwareSearchInput = document.getElementById("softwareSearchInput");

  if (softwareSearchBtn && softwareSearchInput) {
    softwareSearchBtn.addEventListener("click", () => {
      renderSoftware(softwareSearchInput.value);
    });
    softwareSearchInput.addEventListener("keypress", (e) => {
      if (e.key === "Enter") renderSoftware(softwareSearchInput.value);
    });
  }

  if (softwareListBtn) {
    softwareListBtn.addEventListener("click", () => {
      if (softwareSearchInput) softwareSearchInput.value = "";
      renderSoftware(null);
    });
  }

  setInterval(() => {
    updateStatus();
    renderSessions();
  }, 5000);
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

function renderSoftware(searchQuery = null) {
  const tableBody = document.getElementById("softwareTable");
  if (!tableBody) {
    return;
  }
  
  tableBody.innerHTML = `<tr><td colspan="4" style="text-align:center;">Loading...</td></tr>`;

  let fetchPromise;
  if (searchQuery) {
    fetchPromise = fetch(`/api/software/search?q=${encodeURIComponent(searchQuery)}`);
  } else {
    fetchPromise = fetch('/api/software/list');
  }

  fetchPromise
    .then(res => res.json())
    .then((packages) => {
      tableBody.innerHTML = "";
      if (packages.length === 0) {
        tableBody.innerHTML = `<tr><td colspan="4" style="text-align:center;">No packages found.</td></tr>`;
        return;
      }
      
      packages.forEach((pkg) => {
        const row = document.createElement("tr");
        row.innerHTML = `
          <td>${pkg.name}</td>
          <td>${pkg.version}</td>
          <td class="status-cell">${pkg.status}</td>
          <td><button class="${pkg.status === 'Installed' ? 'danger-button' : 'ghost-button'} install-button" data-package="${pkg.name}" data-action="${pkg.status === 'Installed' ? 'remove' : 'install'}">${pkg.status === 'Installed' ? 'Remove' : 'Install'}</button></td>
        `;
        tableBody.appendChild(row);
      });

      tableBody.querySelectorAll(".install-button").forEach((button) => {
        button.addEventListener("click", () => {
          handleInstall(button);
        });
      });
    })
    .catch(err => {
      tableBody.innerHTML = `<tr><td colspan="4" style="text-align:center; color: #f44336;">Error loading packages</td></tr>`;
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
    if (status.status === "error") {
      setStatText("cpu", `Error`);
      setStatText("ram", `API Error`);
      setStatText("disk", `${status.error}`);
      setStatText("cpu-detail", `Err`);
      setStatText("ram-detail", `API Err`);
      setStatText("disk-detail", `Err`);
    } else {
      const cpuText = status.cpu !== null ? `${status.cpu}%` : 'N/A';
      setStatText("cpu", `CPU ${cpuText}`);
      setStatText("ram", `RAM ${status.ram.used} / ${status.ram.total} GB`);
      setStatText("disk", `Disk ${status.disk.used} / ${status.disk.total} GB`);
      setStatText("cpu-detail", cpuText);
      setStatText("ram-detail", `${status.ram.used} / ${status.ram.total} GB`);
      setStatText("disk-detail", `${status.disk.used} / ${status.disk.total} GB`);

      const bat = status.battery;
      if (bat) {
        const charge = bat.charging ? '⚡' : '';
        setStatText("battery", `Bat ${bat.percent}%${charge}`);
        setStatText("bat-detail", `${bat.percent}% ${charge}`);
        setStatText("temp-detail", `${bat.temp} °C`);
        setStatText("temp", `${bat.temp}°C`);
      } else {
        setStatText("battery", `Bat N/A`);
        setStatText("bat-detail", `N/A`);
        setStatText("temp-detail", `-- °C`);
        setStatText("temp", `--°C`);
      }
    }

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
  const pkg = button.getAttribute("data-package");
  const action = button.getAttribute("data-action");
  
  if (action === "remove") {
    button.textContent = "Removing...";
    button.disabled = true;
    
    fetch(`/api/software/action?pkg=${encodeURIComponent(pkg)}&action=remove`, { method: 'POST' })
      .then(res => res.json())
      .then(data => {
        if (data.success) {
          button.textContent = "Install";
          button.disabled = false;
          button.setAttribute("data-action", "install");
          const row = button.closest("tr");
          if (row) row.querySelector(".status-cell").textContent = "Available";
        } else {
          alert("Error removing: " + (data.error || "Unknown error"));
          button.textContent = "Remove";
          button.disabled = false;
        }
      })
      .catch(err => {
        alert("Error: " + err);
        button.textContent = "Remove";
        button.disabled = false;
      });
  } else {
    button.textContent = "Installing...";
    button.disabled = true;
    
    fetch(`/api/software/action?pkg=${encodeURIComponent(pkg)}&action=install`, { method: 'POST' })
      .then(res => res.json())
      .then(data => {
        if (data.success) {
          button.textContent = "Remove";
          button.disabled = false;
          button.setAttribute("data-action", "remove");
          const row = button.closest("tr");
          if (row) row.querySelector(".status-cell").textContent = "Installed";
        } else {
          alert("Error installing: " + (data.error || "Unknown error"));
          button.textContent = "Install";
          button.disabled = false;
        }
      })
      .catch(err => {
        alert("Error: " + err);
        button.textContent = "Install";
        button.disabled = false;
      });
  }
}

function fetchStatus() {
  return fetch('/api/status')
    .then(response => {
        if (!response.ok) throw new Error('API error');
        return response.json();
    })
    .then(data => {
        return {
            status: data.status,
            error: data.error,
            cpu: (data.cpu !== null && data.cpu !== undefined) ? data.cpu : null,
            ram: data.ram && typeof data.ram === 'object' ? data.ram : { used: "0", total: "0" },
            disk: data.disk && typeof data.disk === 'object' ? data.disk : { used: "0", total: "0" },
            battery: data.battery && typeof data.battery === 'object' ? data.battery : null
        };
    })
    .catch(err => {
        return {
            status: "error", error: "Connection failed", cpu: 0, ram: { used: "0", total: "0" }, disk: { used: "0", total: "0" }, battery: null
        };
    });
}

// Removed fetchFilesList()

// Removed fetchSoftwareList() in favor of direct fetch in renderSoftware()

function fetchSessions() {
  return fetch('/api/sessions')
    .then(response => {
        if (!response.ok) throw new Error('API error');
        return response.json();
    })
    .catch(err => {
        return [
          { name: "desktop-01", user: "vncserver", uptime: "-", status: "Error", badge: "danger" },
          { name: "terminal-02", user: "ttyd", uptime: "-", status: "Error", badge: "danger" },
          { name: "files-sync", user: "filebrowser", uptime: "-", status: "Error", badge: "danger" }
        ];
    });
}

function installSoftwarePackage() {
  return new Promise((resolve) => {
    setTimeout(resolve, 1200);
  });
}

function randomBetween(min, max) {
  return Math.random() * (max - min) + min;
}
