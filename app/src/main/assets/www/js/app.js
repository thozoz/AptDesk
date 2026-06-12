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
  const settingsButton = document.getElementById("settingsBtn");
  const connectDesktopBtn = document.getElementById("connectDesktopBtn");
  const openTerminalBtn = document.getElementById("openTerminalBtn");

  setupTabs();
  renderSoftware();
  renderSessions();
  updateStatus();

  // Wire up new buttons
  if (settingsButton) {
    settingsButton.addEventListener("click", () => {
      showSettingsModal();
    });
  }

  if (connectDesktopBtn) {
    connectDesktopBtn.addEventListener("click", () => {
      setActiveTab("desktop");
    });
  }

  if (openTerminalBtn) {
    openTerminalBtn.addEventListener("click", () => {
      setActiveTab("terminal");
    });
  }

  if (refreshButton) {
    refreshButton.addEventListener("click", updateStatus);
  }

  if (statsRefreshButton) {
    statsRefreshButton.addEventListener("click", updateStatus);
  }

  const softwareSearchBtn = document.getElementById("softwareSearchBtn");
  const softwareListBtn = document.getElementById("softwareListBtn");
  const softwareSearchInput = document.getElementById("softwareSearchInput");
  const fixFilebrowserBtn = document.getElementById("fixFilebrowserBtn");

  if (fixFilebrowserBtn) {
    fixFilebrowserBtn.addEventListener("click", () => {
      if (confirm("Are you sure you want to wipe the filebrowser database? This will reset your settings and fix the password prompt issue.")) {
        fixFilebrowserBtn.textContent = "Resetting...";
        fixFilebrowserBtn.disabled = true;
        fetch("/api/fix-filebrowser", { method: "POST" })
          .then(res => res.json())
          .then(data => {
            if (data.success) {
              alert("Database wiped! Please restart the backend to apply changes.");
            } else {
              alert("Error: " + data.error);
            }
            fixFilebrowserBtn.textContent = "Reset Auth DB";
            fixFilebrowserBtn.disabled = false;
          })
          .catch(err => {
            alert("Error: " + err);
            fixFilebrowserBtn.textContent = "Reset Auth DB";
            fixFilebrowserBtn.disabled = false;
          });
      }
    });
  }

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

  const softwareUpdateBtn = document.getElementById("softwareUpdateBtn");
  if (softwareUpdateBtn) {
    softwareUpdateBtn.addEventListener("click", () => {
      softwareUpdateBtn.textContent = "Updating...";
      softwareUpdateBtn.disabled = true;
      fetch("/api/software/update").then(r => r.json()).then(data => {
        softwareUpdateBtn.textContent = "Update";
        softwareUpdateBtn.disabled = false;
        alert(data.success ? "Update completed" : "Update failed:\n" + (data.log || ""));
        if (data.success) renderSoftware(document.getElementById("softwareSearchInput")?.value || null);
      }).catch(() => {
        softwareUpdateBtn.textContent = "Update";
        softwareUpdateBtn.disabled = false;
        alert("Connection error");
      });
    });
  }

  setInterval(() => {
    updateStatus();
    renderSessions();
  }, 5000);
}

function setupTabs() {
  const tabTriggers = document.querySelectorAll(".tree-item, .tab-button");

  tabTriggers.forEach((trigger) => {
    trigger.addEventListener("click", () => {
      const tab = trigger.getAttribute("data-tab");
      if (tab) {
        setActiveTab(tab);
      }
    });
  });

  // Load the desktop iframe immediately on page load
  setTimeout(() => {
    loadTabIframe("desktop");
  }, 500);
}

function loadTabIframe(tab) {
  const iframeId = `iframe-${tab}`;
  const iframe = document.getElementById(iframeId);

  if (!iframe) return;

  // Don't reload if already loaded
  if (iframe.src && iframe.src.length > 0) return;

  switch(tab) {
    case "desktop":
      iframe.src = "/vnc/vnc.html?autoconnect=true&resize=scale&path=vnc/";
      document.querySelector('#tab-desktop .status-text').textContent = "Embedded noVNC interface";
      document.querySelector('#tab-desktop .badge').className = "badge badge-success";
      document.querySelector('#tab-desktop .badge').textContent = "Running";
      break;
    case "terminal":
      iframe.src = "/term/";
      document.querySelector('#tab-terminal .status-text').textContent = "Embedded ttyd interface";
      document.querySelector('#tab-terminal .badge').className = "badge badge-success";
      document.querySelector('#tab-terminal .badge').textContent = "Running";
      break;
    case "files":
      iframe.src = "/filesapp/";
      document.querySelector('#tab-files .status-text').textContent = "Embedded file browser";
      document.querySelector('#tab-files .badge').className = "badge badge-success";
      document.querySelector('#tab-files .badge').textContent = "Running";
      break;
  }
}

function setActiveTab(tab) {
  // Update active state of buttons
  document.querySelectorAll(".tree-item, .tab-button").forEach((item) => {
    item.classList.toggle("active", item.getAttribute("data-tab") === tab);
  });

  // Show/hide panels
  document.querySelectorAll(".tab-panel").forEach((panel) => {
    panel.classList.toggle("active", panel.getAttribute("data-tab") === tab);
  });

  // Load iframe for the tab (debounced)
  let debounceTimer;
  clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => loadTabIframe(tab), 100);
}

// Removed old mock renderFiles()

function showSettingsModal() {
  const modalHtml = `
    <div id="settings-modal" class="modal-overlay">
      <div class="modal-content">
        <h2>Settings</h2>
        <p>AptDesk Configuration</p>
        
        <div class="setting-item">
          <label for="resolution">Display Resolution:</label>
          <select id="resolution" style="width: 100%; padding: 8px; margin-top: 4px;">
            <option value="1280x720">1280x720 (HD)</option>
            <option value="1920x1080">1920x1080 (Full HD)</option>
            <option value="2560x1440">2560x1440 (QHD)</option>
          </select>
        </div>

        <div class="setting-item">
          <label for="autoConnect">Auto-connect on startup:</label>
          <input type="checkbox" id="autoConnect" checked />
        </div>

        <div class="setting-item">
          <button id="saveSettingsBtn" class="primary-button">Save Settings</button>
          <button id="closeSettingsBtn" class="ghost-button">Close</button>
        </div>
      </div>
    </div>
  `;

  document.body.insertAdjacentHTML('beforeend', modalHtml);

  const closeBtn = document.getElementById('closeSettingsBtn');
  const saveBtn = document.getElementById('saveSettingsBtn');
  
  if (closeBtn) {
    closeBtn.addEventListener('click', () => {
      document.getElementById('settings-modal').remove();
    });
  }

  if (saveBtn) {
    saveBtn.addEventListener('click', () => {
      const resolution = document.getElementById('resolution').value;
      const autoConnect = document.getElementById('autoConnect').checked;
      
      // Save to localStorage
      localStorage.setItem('aptdesk.resolution', resolution);
      localStorage.setItem('aptdesk.autoConnect', autoConnect.toString());
      
      alert(`Settings saved!\nResolution: ${resolution}\nAuto-connect: ${autoConnect ? 'Enabled' : 'Disabled'}`);
      document.getElementById('settings-modal').remove();
    });
  }
}

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

// sessionTable doesn't exist in dashboard.html, so we'll just log
function renderSessions() {
  console.log('Sessions:', fetchSessions());
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

      setStatText("uptime-detail", status.uptime || '--');
      setStatText("uptime", `Up ${status.uptime || '--'}`);

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
          button.className = "ghost-button install-button";
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
          button.className = "danger-button install-button";
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
            battery: data.battery && typeof data.battery === 'object' ? data.battery : null,
            uptime: data.uptime || null
        };
    })
    .catch(err => {
        return {
            status: "error", error: "Connection failed", cpu: 0, ram: { used: "0", total: "0" }, disk: { used: "0", total: "0" }, battery: null, uptime: null
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
