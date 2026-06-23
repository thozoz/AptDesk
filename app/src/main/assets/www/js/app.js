"use strict";

let termLoaded = false;
let vncLoaded = false;
let filesLoaded = false;

const STEP_ORDER = ['downloading_rootfs', 'extracting_rootfs', 'copying_assets', 'starting_backend', 'running'];

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
              showGlobalSuccess("Filebrowser database wiped successfully. Restart the backend to apply changes.", 5000);
            } else {
              showErrorBanner("files", "Could not reset filebrowser database: " + data.error, () => fixFilebrowserBtn.click());
            }
            fixFilebrowserBtn.textContent = "Reset Auth DB";
            fixFilebrowserBtn.disabled = false;
          })
          .catch(err => {
            showErrorBanner("files", "Connection error: " + err, () => fixFilebrowserBtn.click());
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
        if (data.success) {
          showGlobalSuccess("Update completed", 5000);
          renderSoftware(document.getElementById("softwareSearchInput")?.value || null);
        } else {
          showErrorBanner("software", "Package update failed: " + (data.log || ""), () => softwareUpdateBtn.click());
        }
      }).catch(() => {
        softwareUpdateBtn.textContent = "Update";
        softwareUpdateBtn.disabled = false;
        showErrorBanner("software", "Connection error while updating packages", () => softwareUpdateBtn.click());
      });
    });
  }

  const retryBtn = document.getElementById("retryBtn");
  if (retryBtn) {
    retryBtn.addEventListener("click", () => {
      // User explicitly opted in by clicking Retry on a visible error — no confirm().
      fetch("/api/restart", { method: "POST" })
        .then(() => updateStatus())
        .catch(() => updateStatus());
    });
  }

  document.querySelectorAll(".connect-now-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      const tab = btn.dataset.tab;
      if (tab) loadTabIframe(tab);
    });
  });

  // PERF-04: gate the poll loop on page visibility — backgrounded tabs should
  // not keep hitting /api/status and /api/sessions (each /api/sessions poll
  // triggers up to 3 PRoot `pidof` spawns server-side). Guard against
  // overlapping in-flight polls so a slow response doesn't stack requests.
  let pollInFlight = false;
  function pollTick() {
    if (document.hidden || pollInFlight) return;
    pollInFlight = true;
    Promise.allSettled([updateStatus(), renderSessions()]).finally(() => {
      pollInFlight = false;
    });
  }
  setInterval(pollTick, 5000);

  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) {
      // Becoming visible again — refresh immediately so the UI isn't stale,
      // then resume the normal 5s cadence via the existing interval.
      pollTick();
    }
  });
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
  let iframeId;
  if (tab === "desktop") {
    iframeId = "vnc-iframe";
  } else if (tab === "terminal") {
    iframeId = "term-iframe";
  } else if (tab === "files") {
    iframeId = "files-iframe";
  } else {
    iframeId = `iframe-${tab}`;
  }
  const iframe = document.getElementById(iframeId);

  if (!iframe) return;

  // Don't reload if already loaded
  if (iframe.src && iframe.src.length > 0) return;

  const notConnected = document.querySelector(`[data-notconnected-for="${tab}"]`);
  const loadingOverlay = document.querySelector(`[data-loading-for="${tab}"]`);
  if (notConnected) notConnected.hidden = true;
  if (loadingOverlay) loadingOverlay.hidden = false;

  // Attach load/error handlers once per iframe.
  if (!iframe.dataset.overlayWired) {
    iframe.dataset.overlayWired = "true";
    iframe.addEventListener("load", () => {
      if (loadingOverlay) loadingOverlay.hidden = true;
    });
    iframe.addEventListener("error", () => {
      if (loadingOverlay) loadingOverlay.hidden = true;
      showErrorBanner(tab, "Service connection failed. Could not load the " + tab + ".", () => {
        iframe.src = iframe.src;
      });
    });
  }

  switch(tab) {
    case "desktop":
      iframe.src = "/vnc/vnc.html?autoconnect=true&resize=scale&path=vnc/";
      break;
    case "terminal":
      iframe.src = "/term/";
      break;
    case "files":
      iframe.src = "/filesapp/";
      break;
  }

  const statusText = document.querySelector(`#tab-${tab} .status-text`);
  const badge = document.querySelector(`#tab-${tab} .badge`);
  if (statusText) {
    if (tab === "desktop") statusText.textContent = "Embedded noVNC interface";
    else if (tab === "terminal") statusText.textContent = "Embedded ttyd interface";
    else if (tab === "files") statusText.textContent = "Embedded file browser";
  }
  if (badge) {
    badge.className = "badge badge-success";
    badge.textContent = "Running";
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
  const savedResolution = localStorage.getItem('aptdesk.resolution') || '1280x720';
  const savedAutoConnect = localStorage.getItem('aptdesk.autoConnect') !== 'false';
  const savedEnableGpu = localStorage.getItem('aptdesk.enableGpu') !== 'false';

  const modalHtml = `
    <div id="settings-modal" class="modal-overlay">
      <div class="modal-content">
        <h2>Settings</h2>
        <p>AptDesk Configuration</p>
        
        <div class="setting-item">
          <label for="resolution">Display Resolution:</label>
          <select id="resolution" class="setting-select">
            <option value="1280x720" ${savedResolution === '1280x720' ? 'selected' : ''}>1280x720 (HD)</option>
            <option value="1920x1080" ${savedResolution === '1920x1080' ? 'selected' : ''}>1920x1080 (Full HD)</option>
            <option value="2560x1440" ${savedResolution === '2560x1440' ? 'selected' : ''}>2560x1440 (QHD)</option>
          </select>
        </div>

        <div class="setting-item" style="margin-top: 12px;">
          <label for="autoConnect">Auto-connect on startup:</label>
          <input type="checkbox" id="autoConnect" ${savedAutoConnect ? 'checked' : ''} />
        </div>

        <div class="setting-item" style="margin-top: 12px; margin-bottom: 16px;">
          <label for="enableGpu" style="display: inline-block; margin-right: 8px;">Enable GPU Acceleration (VirGL):</label>
          <input type="checkbox" id="enableGpu" ${savedEnableGpu ? 'checked' : ''} />
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
      const enableGpu = document.getElementById('enableGpu').checked;
      
      const oldResolution = localStorage.getItem('aptdesk.resolution') || '1280x720';
      const oldEnableGpu = localStorage.getItem('aptdesk.enableGpu') === 'true';

      // Save to localStorage
      localStorage.setItem('aptdesk.resolution', resolution);
      localStorage.setItem('aptdesk.autoConnect', autoConnect.toString());
      localStorage.setItem('aptdesk.enableGpu', enableGpu.toString());
      
      document.getElementById('settings-modal').remove();

      // If resolution or GPU toggle changed, request backend restart
      if (resolution !== oldResolution || enableGpu !== oldEnableGpu) {
        if (confirm("Display settings changed. Would you like to restart the container now to apply changes?")) {
          // Trigger restart
          const formData = new URLSearchParams();
          formData.append('resolution', resolution);
          formData.append('enableGpu', enableGpu.toString());

          fetch('/api/restart', {
            method: 'POST',
            body: formData,
            headers: {
              'Content-Type': 'application/x-www-form-urlencoded'
            }
          })
          .then(res => res.json())
          .then(data => {
            if (data.status === 'restarted') {
              showGlobalSuccess('Backend restarted with new settings', 5000);
              const iframe = document.getElementById('vnc-iframe');
              if (iframe) {
                iframe.src = ""; // reset src to reload
                setTimeout(() => {
                  iframe.src = "/vnc/vnc.html?autoconnect=true&resize=scale&path=vnc/";
                }, 1000);
              }
            } else {
              showErrorBanner("software", 'Could not restart backend: ' + (data.error || 'Unknown error'), () => saveBtn.click());
            }
          })
          .catch(err => {
            showErrorBanner("software", 'Connection error: ' + err, () => saveBtn.click());
          });
        }
      } else {
        showGlobalSuccess('Settings saved', 3000);
      }
    });
  }
}

function renderSoftware(searchQuery = null) {
  const tableBody = document.getElementById("softwareTable");
  if (!tableBody) {
    return;
  }
  
  tableBody.innerHTML = `<tr><td colspan="4" class="loading-row"><span class="loading-dots">Loading</span></td></tr>`;

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
      hideErrorBanner("software");
      if (packages.length === 0) {
        tableBody.innerHTML = `<tr><td colspan="4" class="loading-row">No packages found.</td></tr>`;
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
      tableBody.innerHTML = `<tr><td colspan="4" class="loading-row">Error loading packages</td></tr>`;
      showErrorBanner("software", "Could not load package list", () => renderSoftware(searchQuery));
    });
}

function renderSessions() {
  const list = document.getElementById("sessions-list");
  const empty = document.querySelector('[data-empty-for="sessions"]');
  if (!list) return Promise.resolve();

  return fetchSessions().then((sessions) => {
    const active = Array.isArray(sessions) ? sessions.filter((s) => s.status === "Active") : [];
    if (active.length === 0) {
      list.innerHTML = "";
      if (empty) empty.hidden = false;
      return;
    }
    if (empty) empty.hidden = true;
    list.innerHTML = active
      .map((s) => `<div class="session-row"><span>${s.name}</span><span class="badge badge-${s.badge || "neutral"}">${s.status}</span></div>`)
      .join("");
  });
}

function updateStatus() {
  return fetchStatus().then((status) => {
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

    updateProgressBar(status.backend_state, status.progress, status.error);
    updateEmptyStates(status.backend_state);
  });
}

function updateProgressBar(backendState, progress, errorMessage) {
  const bar = document.getElementById("startup-progress");
  if (!bar) return;

  const currentIndex = STEP_ORDER.indexOf(backendState);
  const isError = backendState === "error";

  STEP_ORDER.forEach((step, index) => {
    const el = bar.querySelector(`[data-step="${step}"]`);
    if (!el) return;
    el.classList.remove("future", "active", "completed", "error");
    if (currentIndex === -1) {
      // idle or unknown: everything future
      el.classList.add("future");
    } else if (index < currentIndex) {
      el.classList.add("completed");
    } else if (index === currentIndex) {
      el.classList.add("active");
    } else {
      el.classList.add("future");
    }
  });

  const errorRow = bar.querySelector(".progress-error");
  const errorText = bar.querySelector(".progress-error-text");
  if (isError) {
    // Mark the last active step (or the first step if none) as error.
    bar.classList.add("error");
    const activeStep = bar.querySelector(".progress-step.active") || bar.querySelector(".progress-step");
    if (activeStep) {
      activeStep.classList.remove("future", "active", "completed");
      activeStep.classList.add("error");
    }
    if (errorRow) errorRow.hidden = false;
    if (errorText) errorText.textContent = errorMessage || "Startup failed.";
  } else {
    bar.classList.remove("error");
    if (errorRow) errorRow.hidden = true;
  }

  // Fade out once running; restore visibility for any other state.
  if (backendState === "running") {
    bar.classList.add("progress-bar--fadeout");
    setTimeout(() => {
      // Only hide if still running (avoid hiding after a regression to error).
      if (bar.classList.contains("progress-bar--fadeout")) {
        bar.style.display = "none";
      }
    }, 600);
  } else {
    bar.classList.remove("progress-bar--fadeout");
    bar.style.display = "";
  }
}

function setStatText(stat, value) {
  document.querySelectorAll(`[data-stat="${stat}"]`).forEach((element) => {
    element.textContent = value;
  });
}

// ── Inline banner helpers (UX-04) ──────────────────────────────────
let globalBannerTimer = null;

function showErrorBanner(context, message, retryFn) {
  const banner = document.querySelector(`[data-error-for="${context}"]`);
  if (!banner) return;
  const text = banner.querySelector(".error-banner-text");
  if (text) text.textContent = message;
  const btn = banner.querySelector(".error-retry-btn");
  if (btn) {
    // Clone+replace to drop any previously-attached retry handler.
    const fresh = btn.cloneNode(true);
    btn.parentNode.replaceChild(fresh, btn);
    fresh.addEventListener("click", () => {
      hideErrorBanner(context);
      if (typeof retryFn === "function") retryFn();
    });
  }
  banner.hidden = false;
}

function hideErrorBanner(context) {
  const banner = document.querySelector(`[data-error-for="${context}"]`);
  if (banner) banner.hidden = true;
}

function showGlobalSuccess(message, autoDismissMs) {
  const banner = document.getElementById("global-banner");
  if (!banner) return;
  banner.className = "success-banner";
  banner.innerHTML = `<span class="success-banner-text"></span>`;
  banner.querySelector(".success-banner-text").textContent = message;
  banner.hidden = false;
  if (globalBannerTimer) clearTimeout(globalBannerTimer);
  globalBannerTimer = setTimeout(hideGlobalBanner, autoDismissMs || 5000);
}

function showGlobalError(message, retryFn) {
  const banner = document.getElementById("global-banner");
  if (!banner) return;
  banner.className = "error-banner";
  banner.innerHTML = `<span class="error-banner-text"></span><button class="ghost-button error-retry-btn" type="button">Retry</button>`;
  banner.querySelector(".error-banner-text").textContent = message;
  banner.querySelector(".error-retry-btn").addEventListener("click", () => {
    hideGlobalBanner();
    if (typeof retryFn === "function") retryFn();
  });
  banner.hidden = false;
  if (globalBannerTimer) { clearTimeout(globalBannerTimer); globalBannerTimer = null; }
}

function hideGlobalBanner() {
  const banner = document.getElementById("global-banner");
  if (banner) banner.hidden = true;
}

// ── Empty-state visibility driven solely by backend_state ──────────
function updateEmptyStates(backendState) {
  const running = backendState === "running";
  [["software", "[data-content-for='software']"], ["stats", "[data-content-for='stats']"]].forEach(([ctx, contentSel]) => {
    const empty = document.querySelector(`[data-empty-for="${ctx}"]`);
    const content = document.querySelector(contentSel);
    if (empty) empty.hidden = running;
    if (content) content.style.display = running ? "" : "none";
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
          showErrorBanner("software", "Failed to remove " + pkg + ": " + (data.error || "Unknown error"), () => button.click());
          button.textContent = "Remove";
          button.disabled = false;
        }
      })
      .catch(err => {
        showErrorBanner("software", "Connection error while removing " + pkg, () => button.click());
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
          showErrorBanner("software", "Failed to install " + pkg + ": " + (data.error || "Unknown error"), () => button.click());
          button.textContent = "Install";
          button.disabled = false;
        }
      })
      .catch(err => {
        showErrorBanner("software", "Connection error while installing " + pkg, () => button.click());
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
            backend_state: data.backend_state || "idle",
            progress: (data.progress !== null && data.progress !== undefined) ? data.progress : null,
            cpu: (data.cpu !== null && data.cpu !== undefined) ? data.cpu : null,
            ram: data.ram && typeof data.ram === 'object' ? data.ram : { used: "0", total: "0" },
            disk: data.disk && typeof data.disk === 'object' ? data.disk : { used: "0", total: "0" },
            battery: data.battery && typeof data.battery === 'object' ? data.battery : null,
            uptime: data.uptime || null
        };
    })
    .catch(err => {
        return {
            status: "error", error: "Connection failed", backend_state: "error", progress: null, cpu: 0, ram: { used: "0", total: "0" }, disk: { used: "0", total: "0" }, battery: null, uptime: null
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
