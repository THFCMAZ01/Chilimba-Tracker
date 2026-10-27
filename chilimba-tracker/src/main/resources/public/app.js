document.addEventListener("DOMContentLoaded", () => {
    refreshLedger();

    // Member Registration
    document.getElementById("member-form").addEventListener("submit", async (e) => {
        e.preventDefault();
        const payload = {
            id: document.getElementById("member-id").value,
            name: document.getElementById("member-name").value,
            phone: document.getElementById("member-phone").value,
            role: document.getElementById("member-role").value
        };

        const res = await fetch("/members", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            alert("Member registered successfully.");
            e.target.reset();
        }
    });

    // Contribution Logging
    document.getElementById("contribution-form").addEventListener("submit", async (e) => {
        e.preventDefault();
        const payload = {
            memberId: document.getElementById("contrib-member-id").value,
            amount: parseFloat(document.getElementById("contrib-amount").value)
        };

        const res = await fetch("/contributions", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            refreshLedger();
            e.target.reset();
        } else {
            const err = await res.json();
            alert("Error: " + err.error);
        }
    });

    // Trigger Payout
    document.getElementById("trigger-payout-btn").addEventListener("click", async () => {
        const res = await fetch("/payouts", { method: "POST" });
        if (res.ok) {
            refreshLedger();
        } else {
            const err = await res.json();
            alert("Error: " + err.error);
        }
    });
});

async function refreshLedger() {
    const tbody = document.getElementById("ledger-body");
    tbody.innerHTML = "";

    try {
        const [contribRes, payoutRes] = await Promise.all([
            fetch("/contributions"),
            fetch("/payouts")
        ]);

        const contributions = await contribRes.json();
        const payouts = await payoutRes.json();

        // Combine and sort by date (fallback to order of execution)
        const combined = [
            ...contributions.map(c => ({ ...c, type: 'CONTRIBUTION' })),
            ...payouts.map(p => ({ ...p, type: 'PAYOUT' }))
        ];

        combined.forEach(record => {
            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td>${record.date}</td>
                <td><strong>${record.type}</strong></td>
                <td>${record.memberId}</td>
                <td>${record.amount.toFixed(2)}</td>
            `;
            tbody.appendChild(tr);
        });
    } catch (e) {
        console.error("Failed to fetch ledger data", e);
    }
}