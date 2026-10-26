window.onerror = function (message, source, lineno, colno, error) {
    console.error("RatesHub UI Error:", message, "at", lineno + ":" + colno, error);
    return false;
};

const $ = (id) => document.getElementById(id);

// Common currency code to English name map to enhance search utility
const CURRENCY_NAMES = {
    USD: "United States Dollar",
    EUR: "Euro",
    INR: "Indian Rupee",
    GBP: "British Pound",
    JPY: "Japanese Yen",
    CAD: "Canadian Dollar",
    AUD: "Australian Dollar",
    CHF: "Swiss Franc",
    CNY: "Chinese Yuan",
    NZD: "New Zealand Dollar",
    SGD: "Singapore Dollar",
    HKD: "Hong Kong Dollar",
    SEK: "Swedish Krona",
    KRW: "South Korean Won",
    TRY: "Turkish Lira",
    RUB: "Russian Ruble",
    BRL: "Brazilian Real",
    ZAR: "South African Rand",
    MXN: "Mexican Peso",
    AED: "UAE Dirham",
    SAR: "Saudi Riyal",
    THB: "Thai Baht",
    MYR: "Malaysian Ringgit",
    IDR: "Indonesian Rupiah",
    PHP: "Philippine Peso",
    PLN: "Polish Zloty",
    NOK: "Norwegian Krone",
    DKK: "Danish Krone",
    HUF: "Hungarian Forint",
    EGP: "Egyptian Pound",
    ILS: "Israeli Shekel",
    KWD: "Kuwaiti Dinar",
    QAR: "Qatari Riyal"
};

let currentFrom = "USD";
let currentTo = "INR";
let activeTimeframe = 7;
let currentRate = 83.24; // Updated dynamically
let activeView = "converter";
let convertTimer = null;
let chartHoverIndex = -1;
let chartPoints = [];

const amountInput = $("amount");
const resultBox = $("result");
const errorBox = $("error");

function showError(msg) {
    errorBox.textContent = msg;
    errorBox.classList.remove("hidden");
    resultBox.classList.add("hidden");
}

function hideError() {
    errorBox.textContent = "";
    errorBox.classList.add("hidden");
    resultBox.classList.remove("hidden");
}

// Custom Seeded Pseudo-Random Number Generator for consistent offline charts
function createRandom(seed) {
    let s = seed;
    return function () {
        s = (s * 9301 + 49297) % 233280;
        return s / 233280;
    };
}

// Format numbers
function formatNum(value, decimals = 2) {
    return new Intl.NumberFormat(undefined, {
        minimumFractionDigits: decimals,
        maximumFractionDigits: decimals
    }).format(value);
}

// ----------------------------------------------------
// View Tab Switcher Controller
// ----------------------------------------------------
function setupViewNavigation() {
    const navItems = document.querySelectorAll(".nav-item");
    const viewPanes = document.querySelectorAll(".view-pane");

    navItems.forEach(item => {
        item.addEventListener("click", () => {
            const viewId = item.getAttribute("data-view");
            if (!viewId) return;

            // Remove active classes
            navItems.forEach(i => i.classList.remove("active"));
            viewPanes.forEach(pane => pane.classList.remove("active"));

            // Add active classes
            item.classList.add("active");
            $(`view-${viewId}`).classList.add("active");
            activeView = viewId;

            // Trigger updates per tab view
            if (viewId === "charts") {
                $(`chart-title-text`).textContent = `${currentFrom} to ${currentTo} History`;
                drawChart();
            } else if (viewId === "rates") {
                loadComparisonGrid(currentFrom);
            } else if (viewId === "history") {
                renderHistory();
            } else if (viewId === "assistant") {
                initChatbotGreeting();
            }
            closeAllDropdowns();
        });
    });
}

// ----------------------------------------------------
// Custom Searchable Dropdown Logics
// ----------------------------------------------------
function setupCustomDropdown(wrapperId, onSelect) {
    const wrapper = $(`${wrapperId}-wrapper`);
    const trigger = $(`${wrapperId}-trigger`);
    const search = $(`${wrapperId}-search`);
    const list = $(`${wrapperId}-options-list`);

    trigger.addEventListener("click", (e) => {
        e.stopPropagation();
        const isOpen = wrapper.classList.contains("open");
        closeAllDropdowns();
        if (!isOpen) {
            wrapper.classList.add("open");
            search.focus();
        }
    });

    search.addEventListener("click", (e) => e.stopPropagation());

    search.addEventListener("input", (e) => {
        renderList(e.target.value);
    });

    function renderList(filterText = "") {
        const query = filterText.toLowerCase().trim();
        const filtered = window.CURRENCIES.filter(c => {
            const matchesCode = c.code.toLowerCase().includes(query);
            const fullName = CURRENCY_NAMES[c.code] || "";
            const matchesName = fullName.toLowerCase().includes(query);
            return matchesCode || matchesName;
        });

        list.innerHTML = filtered.map(c => {
            const activeVal = (wrapperId === "from" ? currentFrom : currentTo);
            const isSelected = activeVal === c.code;
            const nameSuffix = CURRENCY_NAMES[c.code] ? ` - ${CURRENCY_NAMES[c.code]}` : "";
            const countryCode = c.country || "us";
            return `
                <div class="custom-option ${isSelected ? 'selected' : ''}" data-value="${c.code}" data-country="${countryCode}">
                    <img class="flag-img" src="https://flagcdn.com/w40/${countryCode.toLowerCase()}.png" alt="${c.code}">
                    <span>${c.code}${nameSuffix}</span>
                </div>
            `;
        }).join("");

        list.querySelectorAll(".custom-option").forEach(item => {
            item.addEventListener("click", () => {
                const val = item.getAttribute("data-value");
                const country = item.getAttribute("data-country");
                onSelect(val, country);
                wrapper.classList.remove("open");
                search.value = "";
            });
        });
    }

    wrapper.render = renderList;
}

function updateDropdownTrigger(wrapperId, code, country) {
    const countryCode = country || "us";
    $(`${wrapperId}-flag-ui`).innerHTML = `<img class="flag-img" src="https://flagcdn.com/w40/${countryCode.toLowerCase()}.png" alt="${code}">`;
    $(`${wrapperId}-code-ui`).textContent = code;
}

function closeAllDropdowns() {
    document.querySelectorAll(".custom-select-wrapper").forEach(w => {
        w.classList.remove("open");
        const s = w.querySelector(".custom-select-search");
        if (s) s.value = "";
    });
}

document.addEventListener("click", () => closeAllDropdowns());

// ----------------------------------------------------
// Core Conversion Actions
// ----------------------------------------------------
function scheduleConvert() {
    clearTimeout(convertTimer);
    convertTimer = setTimeout(convert, 350);
}

async function convert() {
    const amount = amountInput.value.trim();
    if (amount === "" || isNaN(Number(amount))) {
        showError("Please enter a valid amount.");
        return;
    }
    if (Number(amount) < 0) {
        showError("Amount cannot be negative.");
        return;
    }

    const params = new URLSearchParams({
        from: currentFrom,
        to: currentTo,
        amount: amount
    });

    try {
        const response = await fetch(`/api/convert?${params}`);
        const data = await response.json();

        if (!response.ok) {
            showError(data.error || "Conversion failed.");
            return;
        }

        hideError();

        // Draw country flags in the result section using images
        const toCountry = data.toCountry || "in";
        const fromCountry = data.fromCountry || "us";

        $("result-main").innerHTML = `<img class="flag-img" src="https://flagcdn.com/w40/${fromCountry.toLowerCase()}.png" alt="${data.from}"> ${formatNum(data.amount)} ${data.from}  <span class="result-arrow">&rarr;</span>  <img class="flag-img" src="https://flagcdn.com/w40/${toCountry.toLowerCase()}.png" alt="${data.to}"> <strong>${formatNum(data.result)} ${data.to}</strong>`;
        $("result-rate").innerHTML = `1 ${data.from} = ${formatNum(data.rate, 5)} ${data.to}`;
        $("source").textContent = `Rates: ${data.source}`;

        currentRate = Number(data.rate);

        // Backend automatically stores conversion logs now
    } catch (networkError) {
        showError("Unable to reach the server. Is it online?");
    }
}

// ----------------------------------------------------
// Seeded Random-Walk Historical Rates Canvas Plotter
// ----------------------------------------------------
function drawChart() {
    const canvas = $("historicalChart");
    const ctx = canvas.getContext("2d");
    if (!ctx || activeView !== "charts") return;

    const rect = canvas.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    const width = rect.width || 500;
    const height = rect.height || 240;

    canvas.width = width * dpr;
    canvas.height = height * dpr;
    ctx.scale(dpr, dpr);

    const paddingLeft = 45;
    const paddingRight = 10;
    const paddingTop = 15;
    const paddingBottom = 20;
    const chartW = width - paddingLeft - paddingRight;
    const chartH = height - paddingTop - paddingBottom;

    // Seed variables based on date + currency codes
    const today = new Date();
    const dateStr = today.toISOString().split("T")[0];
    const seed = `${currentFrom}-${currentTo}-${dateStr}`.split("").reduce((acc, c) => acc + c.charCodeAt(0), 0);
    const rand = createRandom(seed);

    const dates = [];
    const rates = [];
    let walkingRate = currentRate;

    for (let i = activeTimeframe - 1; i >= 0; i--) {
        rates[i] = walkingRate;
        const d = new Date();
        d.setDate(today.getDate() - (activeTimeframe - 1 - i));
        dates[i] = d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
        const step = (rand() - 0.5) * 0.024;
        walkingRate = walkingRate * (1 - step);
    }

    const minVal = Math.min(...rates) * 0.995;
    const maxVal = Math.max(...rates) * 1.005;
    const range = maxVal - minVal;

    chartPoints = rates.map((val, idx) => {
        const x = paddingLeft + (idx / (activeTimeframe - 1)) * chartW;
        const y = paddingTop + chartH - ((val - minVal) / range) * chartH;
        return { x, y, val, date: dates[idx] };
    });

    ctx.clearRect(0, 0, width, height);

    // Draw Grid lines
    ctx.strokeStyle = "rgba(255, 255, 255, 0.04)";
    ctx.lineWidth = 1;
    ctx.setLineDash([4, 4]);
    ctx.fillStyle = "rgba(156, 163, 175, 0.5)";
    ctx.font = "10px Outfit";
    ctx.textAlign = "right";
    ctx.textBaseline = "middle";

    const lines = 3;
    for (let i = 0; i <= lines; i++) {
        const factor = i / lines;
        const y = paddingTop + chartH * factor;
        const valLabel = maxVal - range * factor;

        ctx.beginPath();
        ctx.moveTo(paddingLeft, y);
        ctx.lineTo(paddingLeft + chartW, y);
        ctx.stroke();

        ctx.fillText(formatNum(valLabel, valLabel > 10 ? 2 : 4), paddingLeft - 8, y);
    }

    // Draw X-Axis labels
    ctx.textAlign = "center";
    ctx.textBaseline = "top";
    ctx.fillStyle = "rgba(156, 163, 175, 0.6)";

    const stride = activeTimeframe === 30 ? 6 : 1;
    for (let i = 0; i < activeTimeframe; i += stride) {
        ctx.fillText(dates[i], chartPoints[i].x, paddingTop + chartH + 6);
    }

    // Draw Curve Line
    ctx.setLineDash([]);
    ctx.strokeStyle = "#3b82f6";
    ctx.lineWidth = 2.5;
    ctx.beginPath();
    ctx.moveTo(chartPoints[0].x, chartPoints[0].y);
    for (let i = 1; i < chartPoints.length; i++) {
        ctx.lineTo(chartPoints[i].x, chartPoints[i].y);
    }
    ctx.stroke();

    // Area Gradient Fill
    ctx.lineTo(chartPoints[chartPoints.length - 1].x, paddingTop + chartH);
    ctx.lineTo(chartPoints[0].x, paddingTop + chartH);
    ctx.closePath();

    const areaGrad = ctx.createLinearGradient(0, paddingTop, 0, paddingTop + chartH);
    areaGrad.addColorStop(0, "rgba(59, 130, 246, 0.16)");
    areaGrad.addColorStop(1, "rgba(59, 130, 246, 0.0)");
    ctx.fillStyle = areaGrad;
    ctx.fill();

    // Draw Tooltips
    if (chartHoverIndex !== -1 && chartHoverIndex < chartPoints.length) {
        const p = chartPoints[chartHoverIndex];

        ctx.strokeStyle = "rgba(255, 255, 255, 0.15)";
        ctx.lineWidth = 1;
        ctx.setLineDash([2, 2]);
        ctx.beginPath();
        ctx.moveTo(p.x, paddingTop);
        ctx.lineTo(p.x, paddingTop + chartH);
        ctx.stroke();
        ctx.setLineDash([]);

        ctx.fillStyle = "#60a5fa";
        ctx.beginPath();
        ctx.arc(p.x, p.y, 5, 0, 2 * Math.PI);
        ctx.fill();
        ctx.strokeStyle = "#ffffff";
        ctx.lineWidth = 1.5;
        ctx.stroke();

        const tooltipW = 105;
        const tooltipH = 34;
        let tooltipX = p.x - tooltipW / 2;
        let tooltipY = p.y - tooltipH - 8;

        if (tooltipX < paddingLeft) tooltipX = paddingLeft;
        if (tooltipX + tooltipW > width) tooltipX = width - tooltipW;
        if (tooltipY < paddingTop) tooltipY = p.y + 8;

        ctx.fillStyle = "rgba(15, 19, 38, 0.92)";
        ctx.strokeStyle = "rgba(255, 255, 255, 0.15)";
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.roundRect(tooltipX, tooltipY, tooltipW, tooltipH, 6);
        ctx.fill();
        ctx.stroke();

        ctx.fillStyle = "#ffffff";
        ctx.font = "bold 9px Outfit";
        ctx.textAlign = "left";
        ctx.fillText(p.date, tooltipX + 8, tooltipY + 8);

        ctx.fillStyle = "#9ca3af";
        ctx.font = "9px Outfit";
        ctx.fillText(`1 ${currentFrom} = ${formatNum(p.val, p.val > 10 ? 2 : 4)} ${currentTo}`, tooltipX + 8, tooltipY + 20);
    }
}

// Hover event for canvas
const canvas = $("historicalChart");
canvas.addEventListener("mousemove", (e) => {
    if (activeView !== "charts") return;
    const rect = canvas.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;

    let nearestIdx = -1;
    let minDist = Infinity;

    chartPoints.forEach((p, idx) => {
        const dist = Math.abs(p.x - mouseX);
        if (dist < minDist) {
            minDist = dist;
            nearestIdx = idx;
        }
    });

    if (nearestIdx !== -1 && nearestIdx !== chartHoverIndex) {
        chartHoverIndex = nearestIdx;
        drawChart();
    }
});

canvas.addEventListener("mouseleave", () => {
    chartHoverIndex = -1;
    drawChart();
});

window.addEventListener("resize", () => {
    if (activeView === "charts") drawChart();
});

document.querySelectorAll(".timeframe-btn").forEach(btn => {
    btn.addEventListener("click", () => {
        document.querySelectorAll(".timeframe-btn").forEach(b => b.classList.remove("active"));
        btn.classList.add("active");
        activeTimeframe = parseInt(btn.getAttribute("data-days"), 10);
        drawChart();
    });
});

// ----------------------------------------------------
// Popular Comparisons Grid Loader
// ----------------------------------------------------
async function loadComparisonGrid(fromCode) {
    const targets = ["USD", "EUR", "GBP", "INR", "JPY", "AUD", "CAD"]
        .filter(c => c !== fromCode)
        .slice(0, 6);

    const grid = $("comparison-grid");
    grid.innerHTML = "";
    $("base-badge").textContent = `1 ${fromCode}`;

    const fetches = targets.map(async (toCode) => {
        try {
            const res = await fetch(`/api/convert?from=${fromCode}&to=${toCode}&amount=1`);
            if (res.ok) {
                return await res.json();
            }
        } catch (err) {
            console.error("Comparison load error for target", toCode, err);
        }
        return null;
    });

    const results = await Promise.all(fetches);
    results.forEach(d => {
        if (!d) return;
        const card = document.createElement("div");
        card.className = "compare-card";
        const toCountry = d.toCountry || "us";
        card.innerHTML = `
            <div class="compare-title">
                <img class="flag-img" src="https://flagcdn.com/w40/${toCountry.toLowerCase()}.png" alt="${d.to}">
                <span>${d.to}</span>
            </div>
            <div class="compare-rate">${formatNum(d.rate, d.rate > 10 ? 2 : 4)}</div>
        `;
        card.addEventListener("click", () => {
            const targetCurrency = window.CURRENCIES.find(c => c.code === d.to);
            if (targetCurrency) {
                currentTo = targetCurrency.code;
                updateDropdownTrigger("to", targetCurrency.code, targetCurrency.country);

                // Switch back to converter view
                const converterNav = document.querySelector('.nav-item[data-view="converter"]');
                if (converterNav) converterNav.click();
            }
        });
        grid.appendChild(card);
    });
}

// ----------------------------------------------------
// LocalStorage History Operations
// ----------------------------------------------------
async function renderHistory() {
    const list = $("history-list");
    list.innerHTML = `<div class="no-history">Loading history...</div>`;
    try {
        const response = await fetch("/api/history");
        const history = await response.json();
        if (!response.ok) throw new Error();

        if (history.length === 0) {
            list.innerHTML = `<div class="no-history">No recent conversions.</div>`;
            return;
        }

        list.innerHTML = history.map((item, idx) => {
            const fCountry = item.fromCountry || "us";
            const tCountry = item.toCountry || "in";
            return `
                <div class="history-item" data-index="${idx}">
                    <div class="history-item-details">
                        <div class="history-item-math">
                            <span>${formatNum(item.amount)}</span> 
                            <img class="flag-img" src="https://flagcdn.com/w40/${fCountry.toLowerCase()}.png" alt="${item.from}"> 
                            ${item.from} &rarr; 
                            <span>${formatNum(item.result)}</span> 
                            <img class="flag-img" src="https://flagcdn.com/w40/${tCountry.toLowerCase()}.png" alt="${item.to}"> 
                            ${item.to}
                        </div>
                        <div class="history-item-rate">
                            Rate: 1 ${item.from} = ${formatNum(item.rate, 4)} ${item.to}
                        </div>
                    </div>
                    <div class="history-item-arrow">&rarr;</div>
                </div>
            `;
        }).join("");

        list.querySelectorAll(".history-item").forEach(el => {
            el.addEventListener("click", () => {
                const idx = parseInt(el.getAttribute("data-index"), 10);
                const item = history[idx];
                if (item) {
                    amountInput.value = item.amount;

                    currentFrom = item.from;
                    updateDropdownTrigger("from", item.from, item.fromCountry || "us");

                    currentTo = item.to;
                    updateDropdownTrigger("to", item.to, item.toCountry || "in");

                    // Switch back to converter view
                    const converterNav = document.querySelector('.nav-item[data-view="converter"]');
                    if (converterNav) converterNav.click();
                }
            });
        });
    } catch (err) {
        list.innerHTML = `<div class="no-history">Failed to load history from server.</div>`;
    }
}

$("clear-history").addEventListener("click", async () => {
    try {
        await fetch("/api/history", { method: "DELETE" });
    } catch (e) {
        console.error("Failed to clear database history", e);
    }
    renderHistory();
});

// ----------------------------------------------------
// AI Assistant Chatbot Engine
// ----------------------------------------------------
// AI Assistant Chatbot Engine (Powered by Google Gemini with Local Fallback)
// ----------------------------------------------------
const chatThread = $("chat-thread");
const chatInput = $("chat-input");
const chatSend = $("chat-send");
const chatSuggestions = $("chat-suggestions");
const chatClear = $("clear-chat");
const chatMic = $("chat-mic");

let chatGreetingInitialized = false;
let speechRecognizer = null;
let isRecording = false;

function initChatbotGreeting() {
    if (chatGreetingInitialized) return;
    chatGreetingInitialized = true;

    appendChatMessage("bot", 
        "Welcome to <strong>RatesHub AI</strong>. Ask any currency conversion (e.g. <em>'100 USD to EUR'</em> or <em>'500 dollars in rupees'</em>) or travel budget calculation."
    );
}

function appendChatMessage(sender, htmlContent, meta = {}) {
    const indicator = $("typing-indicator-node");
    if (indicator) indicator.remove();

    const msg = document.createElement("div");
    msg.className = `chat-msg ${sender}`;

    let actionsHtml = "";
    if (meta.actions && meta.actions.from && meta.actions.to) {
        const from = meta.actions.from;
        const to = meta.actions.to;
        const amt = meta.actions.amount || 100;
        actionsHtml = `
            <div class="chat-actions">
                <button class="chat-action-btn" onclick="applyConverterFromChat('${from}', '${to}', ${amt})">
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" style="vertical-align:middle; margin-right:4px;">
                        <path d="M20 7H4M4 7L8 3M4 7L8 11" stroke="url(#silverGrad)" stroke-width="2" stroke-linecap="round"/>
                        <path d="M4 17H20M20 17L16 13M20 17L16 21" stroke="url(#goldGrad)" stroke-width="2" stroke-linecap="round"/>
                    </svg>
                    Open ${from} &rarr; ${to} in Converter
                </button>
                <button class="chat-action-btn" onclick="openChartFromChat('${from}', '${to}')">
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" style="vertical-align:middle; margin-right:4px;">
                        <path d="M3 20H21" stroke="url(#silverGrad)" stroke-width="1.8"/>
                        <path d="M4 16L9 11L14 15L21 6" stroke="url(#goldGrad)" stroke-width="2"/>
                    </svg>
                    View ${from}/${to} Trend Chart
                </button>
            </div>
        `;
    }

    const botAvatar = `
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none">
            <rect x="5" y="5" width="14" height="14" rx="4" stroke="url(#silverGrad)" stroke-width="1.8" fill="rgba(4, 22, 38, 0.6)"/>
            <circle cx="12" cy="12" r="3" fill="url(#goldGrad)"/>
            <path d="M12 1V5M12 19V23M1 12H5M19 12H23" stroke="url(#goldGrad)" stroke-width="1.8" stroke-linecap="round"/>
        </svg>
    `;

    const userAvatar = `
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none">
            <circle cx="12" cy="8" r="4" stroke="url(#silverGrad)" stroke-width="1.8" fill="rgba(255, 255, 255, 0.08)"/>
            <path d="M5 20C5 16.5 8 14 12 14C16 14 19 16.5 19 20" stroke="url(#goldGrad)" stroke-width="1.8" stroke-linecap="round"/>
        </svg>
    `;

    msg.innerHTML = `
        <div class="msg-avatar">${sender === "bot" ? botAvatar : userAvatar}</div>
        <div class="msg-bubble">
            <div>${htmlContent}</div>
            ${actionsHtml}
        </div>
    `;

    chatThread.appendChild(msg);
    chatThread.scrollTop = chatThread.scrollHeight;
}

function showTypingIndicator() {
    const indicator = $("typing-indicator-node");
    if (indicator) return;

    const typ = document.createElement("div");
    typ.id = "typing-indicator-node";
    typ.className = "typing-indicator";
    typ.innerHTML = `
        <div class="typing-dot"></div>
        <div class="typing-dot"></div>
        <div class="typing-dot"></div>
    `;
    chatThread.appendChild(typ);
    chatThread.scrollTop = chatThread.scrollHeight;
}

// Format markdown from Gemini (bold, italic, code, bullet points, linebreaks)
function formatChatMarkdown(text) {
    if (!text) return "";
    let safe = text
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");

    // Bold: **text**
    safe = safe.replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>");
    // Italic: *text*
    safe = safe.replace(/\*([^\*\n]+)\*/g, "<em>$1</em>");
    // Inline code: `text`
    safe = safe.replace(/`([^`\n]+)`/g, "<code class='chat-code'>$1</code>");
    // Bullet list items
    safe = safe.replace(/^\s*[\*\-]\s+(.*)$/gim, "<li>$1</li>");
    safe = safe.replace(/(<li>.*<\/li>)/gs, "<ul>$1</ul>");
    // Line breaks
    safe = safe.replace(/\n\n/g, "<br/><br/>").replace(/\n/g, "<br/>");
    return safe;
}

// Detect currency pairs for quick action buttons
function detectCurrencyActions(text) {
    if (!text || !window.CURRENCIES) return null;
    const codes = window.CURRENCIES.map(c => c.code);
    const words = text.toUpperCase().match(/\b[A-Z]{3}\b/g) || [];
    const found = [];
    for (const w of words) {
        if (codes.includes(w) && !found.includes(w)) {
            found.push(w);
        }
    }

    // Check for amount
    const amtMatch = text.match(/\b(\d+(?:\.\d+)?)\b/);
    const amount = amtMatch ? parseFloat(amtMatch[1]) : 100;

    if (found.length >= 2) {
        return { from: found[0], to: found[1], amount: amount };
    }
    return null;
}

// Action button handlers to bridge chat with other tabs
window.applyConverterFromChat = function(from, to, amount) {
    if (amount) amountInput.value = amount;
    currentFrom = from;
    const fCurr = window.CURRENCIES.find(c => c.code === from);
    updateDropdownTrigger("from", from, fCurr ? fCurr.country : "us");

    currentTo = to;
    const tCurr = window.CURRENCIES.find(c => c.code === to);
    updateDropdownTrigger("to", to, tCurr ? tCurr.country : "in");

    // Switch to converter view
    const convTab = document.querySelector('.nav-item[data-view="converter"]');
    if (convTab) convTab.click();
    convert();
};

window.openChartFromChat = function(from, to) {
    currentFrom = from;
    const fCurr = window.CURRENCIES.find(c => c.code === from);
    updateDropdownTrigger("from", from, fCurr ? fCurr.country : "us");

    currentTo = to;
    const tCurr = window.CURRENCIES.find(c => c.code === to);
    updateDropdownTrigger("to", to, tCurr ? tCurr.country : "in");

    const chartTab = document.querySelector('.nav-item[data-view="charts"]');
    if (chartTab) chartTab.click();
};

// Process user chatbot query
async function handleUserChatMessage(text) {
    const rawQuery = text.trim();
    if (!rawQuery) return;

    appendChatMessage("user", rawQuery);
    chatInput.value = "";
    showTypingIndicator();

    try {
        const response = await fetch("/api/chat", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ message: rawQuery })
        });

        const data = await response.json();
        if (response.ok && data.reply) {
            const formatted = formatChatMarkdown(data.reply);
            const actions = detectCurrencyActions(rawQuery + " " + data.reply);
            appendChatMessage("bot", formatted, { actions: actions });
            updateSuggestionsAfterQuery(rawQuery, actions);
            return;
        }
    } catch (networkError) {
        console.warn("Backend chat endpoint failed, using local offline fallback", networkError);
    }

    // Offline / Fallback local handler
    setTimeout(() => {
        performLocalChatResponse(rawQuery);
    }, 400);
}

// Intelligent AI Assistant Response Engine
async function performLocalChatResponse(rawQuery) {
    const query = rawQuery.toLowerCase();

    // 1. Expand currency nicknames & colloquial terms
    let normalized = query
        .replace(/\bcanadian dollars?\b/g, "cad")
        .replace(/\baussie dollars?\b/g, "aud")
        .replace(/\bus dollars?\b/g, "usd")
        .replace(/\bdollars?\b/g, "usd")
        .replace(/\bbucks?\b/g, "usd")
        .replace(/\bquid\b/g, "gbp")
        .replace(/\bpounds?\b/g, "gbp")
        .replace(/\beuros?\b/g, "eur")
        .replace(/\byen\b/g, "jpy")
        .replace(/\brupees?\b/g, "inr")
        .replace(/\bloonies?\b/g, "cad")
        .replace(/\bdirhams?\b/g, "aed")
        .replace(/\bfrancs?\b/g, "chf")
        .replace(/\bbaht\b/g, "thb")
        .replace(/\bwon\b/g, "krw")
        .replace(/\byuan\b/g, "cny")
        .replace(/\bpesos?\b/g, "mxn")
        .replace(/\bdinars?\b/g, "kwd");

    // 2. Extract all recognized 3-letter currency codes
    const words = normalized.toUpperCase().match(/\b[A-Z]{3}\b/g) || [];
    const validCodes = [];
    if (window.CURRENCIES) {
        const supported = window.CURRENCIES.map(c => c.code);
        for (const w of words) {
            if (supported.includes(w) && !validCodes.includes(w)) {
                validCodes.push(w);
            }
        }
    }

    // 3. Extract amount (e.g. 100, 250.50)
    const amountMatch = normalized.match(/\b(\d+(?:\.\d+)?)\b/);
    const amount = amountMatch ? parseFloat(amountMatch[1]) : 100;

    // Pattern A: Two currencies found (e.g. USD and INR)
    if (validCodes.length >= 2) {
        await performChatbotConversion(amount, validCodes[0], validCodes[1]);
        return;
    }

    // Pattern B: One currency found with amount -> convert against active currentTo
    if (validCodes.length === 1) {
        const target = (validCodes[0] === currentFrom) ? currentTo : currentFrom;
        await performChatbotConversion(amount, validCodes[0], target);
        return;
    }

    // Pattern C: Conversational intents
    if (query.includes("how") || query.includes("use") || query.includes("help")) {
        appendChatMessage("bot", 
            "To convert currencies, open the <b>Converter tab</b>, enter an amount, and choose any of the 155 countries. You can also view live 7D/30D trend charts or ask me to convert directly here in the chat!"
        );
    } else if (query.includes("list") || query.includes("support") || query.includes("currency") || query.includes("currencies")) {
        appendChatMessage("bot", 
            "RatesHub supports <strong>155 global currencies</strong> including USD, EUR, INR, GBP, JPY, CAD, AUD, AED, CHF, and CNY with real-time daily exchange rates. Select any dropdown to see flags and names."
        );
    } else if (query.includes("hi") || query.includes("hello") || query.includes("hey")) {
        appendChatMessage("bot", 
            "Hello! I am your <strong>RatesHub AI Assistant</strong>. I can convert currencies, explain exchange rates, and calculate travel budgets.<br/><br/>Try typing: <b>'convert 250 USD to EUR'</b> or <b>'1000 bucks to INR'</b>!"
        );
    } else {
        appendChatMessage("bot", 
            "I am ready to help you with any currency conversion! Simply type what you want to calculate, for example: <b>'convert 100 USD to EUR'</b> or <b>'500 dollars in rupees'</b>."
        );
    }
}

async function performChatbotConversion(amount, from, to) {
    const validFrom = window.CURRENCIES ? window.CURRENCIES.find(c => c.code === from) : null;
    const validTo = window.CURRENCIES ? window.CURRENCIES.find(c => c.code === to) : null;

    if (!validFrom || !validTo) {
        appendChatMessage("bot", `Sorry, code <strong>${from}</strong> or <strong>${to}</strong> is not recognized. Please use supported currency codes like USD, EUR, INR, GBP, etc.`);
        return;
    }

    try {
        const res = await fetch(`/api/convert?from=${from}&to=${to}&amount=${amount}`);
        const data = await res.json();

        if (res.ok) {
            const fCountry = data.fromCountry || "us";
            const tCountry = data.toCountry || "in";
            appendChatMessage("bot", 
                `<strong>Conversion Result:</strong><br/>` +
                `<b>${formatNum(amount)} <img class="flag-img" src="https://flagcdn.com/w40/${fCountry.toLowerCase()}.png" alt="${from}"> ${from}</b> = ` +
                `<strong>${formatNum(data.result)} <img class="flag-img" src="https://flagcdn.com/w40/${tCountry.toLowerCase()}.png" alt="${to}"> ${to}</strong><br/>` +
                `<em>(Live Exchange Rate: 1 ${from} = ${formatNum(data.rate, 4)} ${to})</em>`,
                { actions: { from, to, amount } }
            );

            // Update suggestion chips for next steps
            updateSuggestionsAfterQuery(from + " " + to, { from, to, amount });
        } else {
            appendChatMessage("bot", `Failed to complete conversion: ${data.error}`);
        }
    } catch (e) {
        appendChatMessage("bot", "I could not reach the conversion server. Please check backend connection.");
    }
}

function updateSuggestionsAfterQuery(query, actions) {
    if (!chatSuggestions) return;
    if (actions && actions.from && actions.to) {
        chatSuggestions.innerHTML = `
            <button class="suggest-badge" data-query="What is the 30-day forecast for ${actions.from} vs ${actions.to}?">30D ${actions.from}/${actions.to} Outlook</button>
            <button class="suggest-badge" data-query="Compare ${actions.from} with EUR, GBP, and JPY">Compare with EUR/GBP/JPY</button>
            <button class="suggest-badge" data-query="Calculate a 15% tip on ${actions.amount || 100} ${actions.from}">Calculate 15% Tip</button>
            <button class="suggest-badge" data-query="What is the best time to exchange ${actions.from} to ${actions.to}?">Best Exchange Time</button>
        `;
        bindSuggestionBadges();
    }
}

function bindSuggestionBadges() {
    document.querySelectorAll(".suggest-badge").forEach(badge => {
        badge.onclick = () => {
            const q = badge.getAttribute("data-query");
            if (q) handleUserChatMessage(q);
        };
    });
}

// Clear Chat Event
if (chatClear) {
    chatClear.addEventListener("click", () => {
        chatThread.innerHTML = "";
        chatGreetingInitialized = false;
        initChatbotGreeting();
    });
}

// Speech-to-Text Microphone Hook
if (chatMic) {
    const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (SpeechRec) {
        speechRecognizer = new SpeechRec();
        speechRecognizer.continuous = false;
        speechRecognizer.interimResults = false;
        speechRecognizer.lang = "en-US";

        speechRecognizer.onstart = () => {
            isRecording = true;
            chatMic.classList.add("recording");
            chatInput.placeholder = "Listening... Speak now";
        };

        speechRecognizer.onresult = (event) => {
            const transcript = event.results[0][0].transcript;
            if (transcript) {
                chatInput.value = transcript;
                handleUserChatMessage(transcript);
            }
        };

        speechRecognizer.onerror = (e) => {
            console.warn("Speech recognition error:", e);
        };

        speechRecognizer.onend = () => {
            isRecording = false;
            chatMic.classList.remove("recording");
            chatInput.placeholder = "Ask RatesHub AI anything (e.g. 'Convert 250 quid to INR' or 'Trip budget')...";
        };

        chatMic.addEventListener("click", () => {
            if (isRecording) {
                speechRecognizer.stop();
            } else {
                speechRecognizer.start();
            }
        });
    } else {
        chatMic.title = "Voice input is not supported in this browser.";
        chatMic.style.opacity = "0.4";
    }
}

// Bind Send Events
chatSend.addEventListener("click", () => handleUserChatMessage(chatInput.value));
chatInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
        handleUserChatMessage(chatInput.value);
    }
});

bindSuggestionBadges();

// ----------------------------------------------------
// Initialization Hook
// ----------------------------------------------------
async function init() {
    setupViewNavigation();

    try {
        const response = await fetch("/api/currencies");
        window.CURRENCIES = await response.json();
    } catch (loadError) {
        window.CURRENCIES = [
            { code: "USD", flag: "🇺🇸", country: "us" },
            { code: "EUR", flag: "🇪🇺", country: "eu" },
            { code: "INR", flag: "🇮🇳", country: "in" },
            { code: "GBP", flag: "🇬🇧", country: "gb" },
            { code: "JPY", flag: "🇯🇵", country: "jp" },
            { code: "CAD", flag: "🇨🇦", country: "ca" },
            { code: "AUD", flag: "🇦🇺", country: "au" }
        ];
        showError("Could not load currency list from server.");
    }

    const usd = window.CURRENCIES.find(c => c.code === "USD") || { code: "USD", flag: "🇺🇸", country: "us" };
    const inr = window.CURRENCIES.find(c => c.code === "INR") || { code: "INR", flag: "🇮🇳", country: "in" };

    updateDropdownTrigger("from", usd.code, usd.country);
    updateDropdownTrigger("to", inr.code, inr.country);

    currentFrom = usd.code;
    currentTo = inr.code;

    setupCustomDropdown("from", (val, country) => {
        currentFrom = val;
        updateDropdownTrigger("from", val, country);
        convert();
    });

    setupCustomDropdown("to", (val, country) => {
        currentTo = val;
        updateDropdownTrigger("to", val, country);
        convert();
    });

    // Initial dropdown renders
    $("from-wrapper").render();
    $("to-wrapper").render();

    amountInput.addEventListener("keydown", (e) => {
        if (e.key === "Enter") {
            convert();
        }
    });

    $("convert-btn").addEventListener("click", convert);

    $("swap").addEventListener("click", () => {
        const tempCode = currentFrom;
        currentFrom = currentTo;
        currentTo = tempCode;

        const fromCurr = window.CURRENCIES.find(c => c.code === currentFrom);
        const toCurr = window.CURRENCIES.find(c => c.code === currentTo);

        if (fromCurr && toCurr) {
            updateDropdownTrigger("from", currentFrom, fromCurr.country);
            updateDropdownTrigger("to", currentTo, toCurr.country);
        }

        $("from-wrapper").render();
        $("to-wrapper").render();

        convert();
    });

    // Run first conversion
    convert();
}

init();
