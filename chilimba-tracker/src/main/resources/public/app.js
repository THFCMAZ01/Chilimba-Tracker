document.addEventListener("DOMContentLoaded", () => {
    refreshLedger();

    // Member Registration
    document.getElementById("member-form").addEventListener("submit", async (e) => {
        e.preventDefault();
        const payload = {
            id: document.getElementById("member-id").value.trim(),
            name: document.getElementById("member-name").value.trim(),
            phone: document.getElementById("member-phone").value.trim(),
            role: document.getElementById("member-role").value
        };

        try {
            const res = await fetch("/members", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });
            if (!res.ok) {
                const err = await res.json();
                throw new Error(err.error || "Member registration failed.");
            }
            alert("Member registered successfully.");
            e.target.reset();
            refreshLedger();
        } catch (error) {
            alert("Error: " + error.message);
        }
    });

    // Contribution Logging
    document.getElementById("contribution-form").addEventListener("submit", async (e) => {
        e.preventDefault();
        const payload = {
            memberId: document.getElementById("contrib-member-id").value.trim(),
            amount: Number(document.getElementById("contrib-amount").value)
        };

        try {
            const res = await fetch("/contributions", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });
            if (!res.ok) {
                const err = await res.json();
                throw new Error(err.error || "Contribution recording failed.");
            }
            refreshLedger();
            e.target.reset();
        } catch (error) {
            alert("Error: " + error.message);
        }
    });

    // Trigger Payout
    document.getElementById("trigger-payout-btn").addEventListener("click", async () => {
        try {
            const res = await fetch("/payouts", { method: "POST" });
            if (!res.ok) {
                const err = await res.json();
                throw new Error(err.error || "Payout could not be completed.");
            }
            refreshLedger();
        } catch (error) {
            alert("Error: " + error.message);
        }
    });
});

async function refreshLedger() {
    const tbody = document.getElementById("ledger-body");
    tbody.replaceChildren();

    try {
        const [contribRes, payoutRes] = await Promise.all([
            fetch("/contributions"),
            fetch("/payouts")
        ]);
        if (!contribRes.ok || !payoutRes.ok) {
            throw new Error("The ledger could not be loaded.");
        }

        const contributions = await contribRes.json();
        const payouts = await payoutRes.json();
        const combined = [
            ...contributions.map(c => ({ ...c, type: "CONTRIBUTION" })),
            ...payouts.map(p => ({ ...p, type: "PAYOUT" }))
        ];

        combined.sort((a, b) => String(a.date).localeCompare(String(b.date)));

        combined.forEach(record => {
            const tr = document.createElement("tr");
            [record.date, record.type, record.memberId, Number(record.amount).toFixed(2)]
                .forEach(value => {
                    const td = document.createElement("td");
                    td.textContent = String(value ?? "");
                    tr.appendChild(td);
                });
            tbody.appendChild(tr);
        });
    } catch (error) {
        console.error("Failed to fetch ledger data", error);
    }
}
