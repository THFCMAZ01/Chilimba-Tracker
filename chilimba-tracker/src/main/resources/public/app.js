document.addEventListener("DOMContentLoaded", () => {
    initialiseAuth();
});

// --------------------------------------------------
// Application state
// --------------------------------------------------

const state = {
    members: [],
    contributions: [],
    payouts: [],
    csrfToken: null,
    currentUser: null,
    dashboardInitialised: false
};

const currencyFormatter = new Intl.NumberFormat("en-ZM", {
    style: "currency",
    currency: "ZMW",
    minimumFractionDigits: 2
});

function formatMoney(amount) {
    return currencyFormatter.format(Number(amount) || 0);
}

function formatDate(date) {
    if (!date) return "—";

    const parsed = new Date(`${date}T00:00:00`);

    if (Number.isNaN(parsed.getTime())) {
        return date;
    }

    return parsed.toLocaleDateString("en-ZM", {
        day: "numeric",
        month: "short",
        year: "numeric"
    });
}

function getMember(memberId) {
    return state.members.find(member => member.id === memberId);
}

function getMemberName(memberId) {
    const member = getMember(memberId);
    return member ? member.name : "Unknown member";
}

function getMemberCode(memberId) {
    const member = getMember(memberId);
    return member ? member.memberCode : "—";
}

// --------------------------------------------------
// API communication
// --------------------------------------------------

async function apiRequest(url, options = {}) {
    const headers = { ...(options.headers || {}) };
    const method = (options.method || "GET").toUpperCase();

    // All authenticated POST requests require a CSRF token.
    // Login is exempt because no authenticated session exists yet.
    if (method === "POST" && state.csrfToken && url !== "/api/auth/login") {
        headers["X-CSRF-Token"] = state.csrfToken;
    }

    const response = await fetch(url, {
        ...options,
        headers,
        credentials: "same-origin"
    });

    if (response.status === 401 && state.currentUser) {
        const message = "Your session has expired. Please sign in again.";
        showLoginScreen(message);
        throw new Error(message);
    }

    if (!response.ok) {
        let message = "Something went wrong.";

        try {
            const error = await response.json();
            message = error.error || message;
        } catch {
            // Use the default message if the response isn't JSON.
        }

        throw new Error(message);
    }

    if (response.status === 204) {
        return null;
    }

    return response.json();
}

// --------------------------------------------------
// Authentication
// --------------------------------------------------

function showLoginScreen(message = "") {
    document.getElementById("app-shell").hidden = true;
    document.getElementById("login-screen").hidden = false;

    const error = document.getElementById("login-error");
    error.textContent = message;
    error.hidden = !message;

    state.csrfToken = null;
    state.currentUser = null;
}

function showAuthenticatedApp(user) {
    state.currentUser = user;
    state.csrfToken = user.csrfToken;

    document.getElementById("login-screen").hidden = true;
    document.getElementById("app-shell").hidden = false;

    document.getElementById("current-user").textContent =
        `${user.name} (${user.role})`;

    if (!state.dashboardInitialised) {
        initialiseNavigation();
        initialiseForms();
        initialisePayouts();
        initialiseDate();
        state.dashboardInitialised = true;
    }

    loadDashboard();
}

async function fetchCurrentUser() {
    return apiRequest("/api/auth/me");
}

async function initialiseAuth() {
    const loginForm = document.getElementById("login-form");

    loginForm.addEventListener("submit", async event => {
        event.preventDefault();

        const email = document.getElementById("login-email").value.trim();
        const passwordInput = document.getElementById("login-password");
        const submitButton = document.getElementById("login-submit");
        const error = document.getElementById("login-error");

        error.hidden = true;
        submitButton.disabled = true;
        submitButton.textContent = "Signing in...";

        try {
            await apiRequest("/api/auth/login", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    email,
                    password: passwordInput.value
                })
            });

            passwordInput.value = "";

            const user = await fetchCurrentUser();
            showAuthenticatedApp(user);

        } catch (err) {
            passwordInput.value = "";
            error.textContent = err.message;
            error.hidden = false;

        } finally {
            submitButton.disabled = false;
            submitButton.textContent = "Sign in";
        }
    });

    document.getElementById("logout-button").addEventListener(
        "click",
        async () => {
            const button = document.getElementById("logout-button");
            button.disabled = true;

            try {
                await apiRequest("/api/auth/logout", {
                    method: "POST"
                });

                showLoginScreen("You have signed out.");
                document.getElementById("login-password").value = "";

            } catch (err) {
                showNotification(err.message, true);

            } finally {
                button.disabled = false;
            }
        }
    );

    try {
        const user = await fetchCurrentUser();
        showAuthenticatedApp(user);

    } catch (err) {
        showLoginScreen();
    }
}

async function loadDashboard() {
    try {
        const [members, contributions, payouts] = await Promise.all([
            apiRequest("/members"),
            apiRequest("/contributions"),
            apiRequest("/payouts")
        ]);

        state.members = members;
        state.contributions = contributions;
        state.payouts = payouts;

        renderDashboard();

    } catch (error) {
        console.error("Dashboard loading failed:", error);
        showNotification(error.message, true);
    }
}

// --------------------------------------------------
// Navigation
// --------------------------------------------------

function initialiseNavigation() {
    const navigationButtons = document.querySelectorAll(
        "[data-view]"
    );

    navigationButtons.forEach(button => {
        button.addEventListener("click", () => {
            const viewName = button.dataset.view;

            document.querySelectorAll(".view").forEach(view => {
                view.classList.remove("active");
            });

            document.querySelectorAll(".nav-item").forEach(item => {
                item.classList.remove("active");
            });

            const selectedView = document.getElementById(
                `${viewName}-view`
            );

            if (!selectedView) return;

            selectedView.classList.add("active");

            const matchingNavigation = document.querySelector(
                `.nav-item[data-view="${viewName}"]`
            );

            if (matchingNavigation) {
                matchingNavigation.classList.add("active");
            }

            const pageTitle = document.getElementById("page-title");

            pageTitle.textContent = button.textContent.trim();

            // Refresh data when entering a page.
            loadDashboard();
        });
    });
}

// --------------------------------------------------
// Date
// --------------------------------------------------

function initialiseDate() {
    document.getElementById("today-date").textContent =
        new Date().toLocaleDateString("en-ZM", {
            weekday: "short",
            day: "numeric",
            month: "long",
            year: "numeric"
        });
}

// --------------------------------------------------
// Notifications
// --------------------------------------------------

let notificationTimeout;

function showNotification(message, isError = false) {
    const notification = document.getElementById("notification");

    notification.textContent = message;
    notification.style.background = isError ? "#a83e3e" : "#293548";
    notification.classList.add("visible");

    clearTimeout(notificationTimeout);

    notificationTimeout = setTimeout(() => {
        notification.classList.remove("visible");
    }, 3500);
}

// --------------------------------------------------
// Form visibility
// --------------------------------------------------

function initialiseForms() {
    const memberCard = document.getElementById("member-form-card");
    const contributionCard = document.getElementById(
        "contribution-form-card"
    );

    document.getElementById("open-member-form").addEventListener(
        "click",
        () => {
            memberCard.hidden = false;
            document.getElementById("member-name").focus();
        }
    );

    document.getElementById("cancel-member-form").addEventListener(
        "click",
        () => {
            memberCard.hidden = true;
            document.getElementById("member-form").reset();
        }
    );

    document.getElementById("open-contribution-form").addEventListener(
        "click",
        () => {
            contributionCard.hidden = false;
            document.getElementById("contrib-member-id").focus();
        }
    );

    document.getElementById("cancel-contribution-form").addEventListener(
        "click",
        () => {
            contributionCard.hidden = true;
            document.getElementById("contribution-form").reset();
        }
    );

    initialiseMemberForm();
    initialiseContributionForm();
}

// --------------------------------------------------
// Member registration
// --------------------------------------------------

function initialiseMemberForm() {
    const form = document.getElementById("member-form");

    form.addEventListener("submit", async event => {
        event.preventDefault();

        const payload = {
            name: document.getElementById("member-name").value.trim(),
            phone: document.getElementById("member-phone").value.trim(),
            role: document.getElementById("member-role").value
        };

        const submitButton = form.querySelector(
            'button[type="submit"]'
        );

        submitButton.disabled = true;
        submitButton.textContent = "Registering...";

        try {
            await apiRequest("/members", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(payload)
            });

            form.reset();
            document.getElementById("member-form-card").hidden = true;

            await loadDashboard();

            showNotification("Member registered successfully.");

        } catch (error) {
            showNotification(error.message, true);

        } finally {
            submitButton.disabled = false;
            submitButton.textContent = "Register member";
        }
    });
}

// --------------------------------------------------
// Contribution recording
// --------------------------------------------------

function initialiseContributionForm() {
    const form = document.getElementById("contribution-form");

    form.addEventListener("submit", async event => {
        event.preventDefault();

        const payload = {
            memberId: document.getElementById("contrib-member-id").value,
            amount: Number(
                document.getElementById("contrib-amount").value
            )
        };

        if (!payload.memberId) {
            showNotification("Please select a member.", true);
            return;
        }

        if (!Number.isFinite(payload.amount) || payload.amount <= 0) {
            showNotification("Enter a valid contribution amount.", true);
            return;
        }

        const submitButton = form.querySelector(
            'button[type="submit"]'
        );

        submitButton.disabled = true;
        submitButton.textContent = "Saving...";

        try {
            await apiRequest("/contributions", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(payload)
            });

            form.reset();

            document.getElementById(
                "contribution-form-card"
            ).hidden = true;

            await loadDashboard();

            showNotification("Contribution recorded successfully.");

        } catch (error) {
            showNotification(error.message, true);

        } finally {
            submitButton.disabled = false;
            submitButton.textContent = "Save contribution";
        }
    });
}

// --------------------------------------------------
// Payout execution
// --------------------------------------------------

function initialisePayouts() {
    const button = document.getElementById("trigger-payout-btn");

    button.addEventListener("click", async () => {
        const confirmed = window.confirm(
            "Execute the payout for the current cycle?\n\n" +
            "This will permanently record the payout and close the cycle."
        );

        if (!confirmed) return;

        button.disabled = true;
        button.textContent = "Processing...";

        try {
            const payout = await apiRequest("/payouts", {
                method: "POST"
            });

            await loadDashboard();

            showNotification(
                `Payout recorded: ${formatMoney(payout.amount)}`
            );

        } catch (error) {
            showNotification(error.message, true);

        } finally {
            button.disabled = false;
            button.textContent = "Execute payout";
        }
    });
}

// --------------------------------------------------
// Dashboard rendering
// --------------------------------------------------

function renderDashboard() {
    renderStatistics();
    renderMembers();
    renderMemberDropdown();
    renderContributions();
    renderPayouts();
    renderLedger();
    renderRecentActivity();
}

function renderStatistics() {
    const totalContributions = state.contributions.reduce(
        (total, contribution) =>
            total + Number(contribution.amount),
        0
    );

    const totalPayouts = state.payouts.reduce(
        (total, payout) => total + Number(payout.amount),
        0
    );

    document.getElementById("total-members").textContent =
        state.members.length;

    document.getElementById("total-contributions").textContent =
        formatMoney(totalContributions);

    document.getElementById("cycle-pool").textContent =
        formatMoney(totalContributions);

    document.getElementById("total-payouts").textContent =
        formatMoney(totalPayouts);
}

// --------------------------------------------------
// Members table and dropdown
// --------------------------------------------------

function renderMembers() {
    const tbody = document.getElementById("members-table");

    tbody.replaceChildren();

    document.getElementById("member-count").textContent =
        `${state.members.length} members`;

    if (state.members.length === 0) {
        showEmptyState(tbody, 5, "No members registered yet.");
        return;
    }

    state.members.forEach(member => {
        const row = document.createElement("tr");

        appendCell(row, member.name);
        appendCell(row, member.memberCode);
        appendCell(row, member.phone);
        appendCell(row, member.roleName);
        appendCell(row, formatDate(member.joinDate));

        tbody.appendChild(row);
    });
}

function renderMemberDropdown() {
    const select = document.getElementById("contrib-member-id");

    const previousValue = select.value;

    select.replaceChildren();

    const placeholder = document.createElement("option");
    placeholder.value = "";
    placeholder.textContent = "Select a member";

    select.appendChild(placeholder);

    state.members.forEach(member => {
        const option = document.createElement("option");

        option.value = member.id;
        option.textContent = `${member.memberCode} — ${member.name}`;

        select.appendChild(option);
    });

    if (state.members.some(member => member.id === previousValue)) {
        select.value = previousValue;
    }
}

// --------------------------------------------------
// Contributions table
// --------------------------------------------------

function renderContributions() {
    const tbody = document.getElementById("contributions-table");

    tbody.replaceChildren();

    document.getElementById("contribution-count").textContent =
        `${state.contributions.length} records`;

    if (state.contributions.length === 0) {
        showEmptyState(tbody, 4, "No contributions recorded this cycle.");
        return;
    }

    state.contributions.forEach(contribution => {
        const row = document.createElement("tr");

        appendCell(row, formatDate(contribution.date));
        appendCell(row, getMemberName(contribution.memberId));
        appendCell(row, getMemberCode(contribution.memberId));

        appendCell(
            row,
            formatMoney(contribution.amount),
            "amount-column"
        );

        tbody.appendChild(row);
    });
}

// --------------------------------------------------
// Payouts table
// --------------------------------------------------

function renderPayouts() {
    const tbody = document.getElementById("payouts-table");

    tbody.replaceChildren();

    document.getElementById("payout-count").textContent =
        `${state.payouts.length} payouts`;

    if (state.payouts.length === 0) {
        showEmptyState(tbody, 3, "No payouts recorded yet.");
        return;
    }

    state.payouts.forEach(payout => {
        const row = document.createElement("tr");

        appendCell(row, formatDate(payout.date));
        appendCell(row, getMemberName(payout.memberId));

        appendCell(
            row,
            formatMoney(payout.amount),
            "amount-column"
        );

        tbody.appendChild(row);
    });
}

// --------------------------------------------------
// Financial ledger
// --------------------------------------------------

function renderLedger() {
    const tbody = document.getElementById("ledger-body");

    tbody.replaceChildren();

    const records = getCombinedRecords();

    document.getElementById("ledger-count").textContent =
        `${records.length} transactions`;

    if (records.length === 0) {
        showEmptyState(tbody, 4, "No financial transactions recorded.");
        return;
    }

    records.forEach(record => {
        const row = document.createElement("tr");

        appendCell(row, formatDate(record.date));
        appendCell(row, getMemberName(record.memberId));
        appendCell(row, record.type);

        appendCell(
            row,
            formatMoney(record.amount),
            "amount-column"
        );

        tbody.appendChild(row);
    });
}

function getCombinedRecords() {
    const contributions = state.contributions.map(record => ({
        ...record,
        type: "Contribution"
    }));

    const payouts = state.payouts.map(record => ({
        ...record,
        type: "Payout"
    }));

    return [...contributions, ...payouts].sort(
        (a, b) => String(b.date).localeCompare(String(a.date))
    );
}

// --------------------------------------------------
// Recent activity
// --------------------------------------------------

function renderRecentActivity() {
    const tbody = document.getElementById("overview-activity");

    tbody.replaceChildren();

    const records = getCombinedRecords().slice(0, 6);

    if (records.length === 0) {
        showEmptyState(tbody, 4, "No recent activity.");
        return;
    }

    records.forEach(record => {
        const row = document.createElement("tr");

        appendCell(row, formatDate(record.date));
        appendCell(row, getMemberName(record.memberId));
        appendCell(row, record.type);

        appendCell(
            row,
            formatMoney(record.amount),
            "amount-column"
        );

        tbody.appendChild(row);
    });
}

// --------------------------------------------------
// Safe table helpers
// --------------------------------------------------

function appendCell(row, value, className = "") {
    const cell = document.createElement("td");

    cell.textContent = String(value ?? "");

    if (className) {
        cell.className = className;
    }

    row.appendChild(cell);
}

function showEmptyState(tbody, columnCount, message) {
    const row = document.createElement("tr");
    const cell = document.createElement("td");

    cell.colSpan = columnCount;
    cell.className = "empty-state";
    cell.textContent = message;

    row.appendChild(cell);
    tbody.appendChild(row);
}