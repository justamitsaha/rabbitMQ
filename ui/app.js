// Configuration
const ORDER_SERVICE_API = window.ORDER_SERVICE_API || 'http://localhost:8080';

// Global State
let ordersMap = new Map();
let activeTrackingOrderId = null;

// DOM Elements
const orderForm = document.getElementById('order-form');
const ordersContainer = document.getElementById('orders-container');
const activeOrderIdLabel = document.getElementById('active-order-id');
const logsContainer = document.getElementById('logs-container');
const timeline = document.getElementById('timeline');

const paymentTypeSelect = document.getElementById('paymentType');
const cardDetailsDiv = document.getElementById('payment-details-card');
const upiDetailsDiv = document.getElementById('payment-details-upi');
const netbankingDetailsDiv = document.getElementById('payment-details-netbanking');

// Toggle visible payment detail inputs dynamically
paymentTypeSelect.addEventListener('change', function () {
    const val = this.value;
    cardDetailsDiv.style.display = val === 'CARD' ? 'block' : 'none';
    upiDetailsDiv.style.display = val === 'UPI' ? 'block' : 'none';
    netbankingDetailsDiv.style.display = val === 'NET_BANKING' ? 'block' : 'none';
});

// --- 1. Form Submission (Place Order via API) ---
orderForm.addEventListener('submit', function (e) {
    e.preventDefault();
    
    const customerId = document.getElementById('customerId').value;
    const customerName = document.getElementById('customerName').value;
    const amount = parseFloat(document.getElementById('amount').value);
    const paymentType = paymentTypeSelect.value;
    
    // Extract payment credentials dynamically based on selection
    let cardNo = null;
    let accountNo = null;
    let upiId = null;
    
    if (paymentType === 'CARD') {
        cardNo = document.getElementById('cardNo').value;
    } else if (paymentType === 'NET_BANKING') {
        accountNo = document.getElementById('accountNo').value;
    } else if (paymentType === 'UPI') {
        upiId = document.getElementById('upiId').value;
    }
    
    // Extract address details dynamically from the form
    const addressLine1 = document.getElementById('addressLine1').value;
    const addressLine2 = document.getElementById('addressLine2').value || null;
    const city = document.getElementById('city').value;
    const state = document.getElementById('state').value;
    const postalCode = document.getElementById('postalCode').value;
    
    // Construct request body conforming to PlaceOrderRequest DTO
    const placeOrderRequest = {
        customerId: customerId,
        customerName: customerName,
        payment: {
            amount: amount,
            paymentType: paymentType,
            cardNo: cardNo,
            accountNo: accountNo,
            upiId: upiId
        },
        delivery: {
            addressLine1: addressLine1,
            addressLine2: addressLine2,
            city: city,
            state: state,
            postalCode: postalCode
        }
    };

    fetch(`${ORDER_SERVICE_API}/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(placeOrderRequest)
    })
    .then(res => {
        if (!res.ok) throw new Error('Ingestion failed');
        return res.json();
    })
    .then(order => {
        addLogEntry('client.request', `POST /orders accepted. Order ID: ${order.orderId}`);
        // Set as active tracking immediately
        selectOrderForTracking(order.orderId);
    })
    .catch(err => {
        addLogEntry('client.error', `Failed to place order: ${err.message}`);
    });
});

// --- 2. Live Orders Feed (SSE Stream) ---
const ordersEventSource = new EventSource(`${ORDER_SERVICE_API}/orders`);

ordersEventSource.onmessage = function (event) {
    const order = JSON.parse(event.data);
    ordersMap.set(order.orderId, order);
    renderOrdersList();
    
    // Update timeline if this order is currently being tracked
    if (order.orderId === activeTrackingOrderId) {
        updateTrackerTimeline(order);
    }
};

ordersEventSource.onerror = function (err) {
    console.error("SSE Orders connection error:", err);
};

// --- 3. Live Event Bus Feed (SSE Stream) ---
const notificationsEventSource = new EventSource(`${ORDER_SERVICE_API}/notifications`);

notificationsEventSource.onmessage = function (event) {
    const data = JSON.parse(event.data);
    const eventType = data.event;
    const payload = data.payload;
    
    // Add to scrolling logs
    addLogEntry(eventType, `[${eventType}] for OrderID: ${payload.orderId || 'N/A'}`);
    
    // If the event corresponds to our tracked order, update the timeline steps in real-time
    if (payload.orderId === activeTrackingOrderId) {
        handleRealTimeStepUpdates(eventType);
    }
};

notificationsEventSource.onerror = function (err) {
    console.error("SSE Notifications connection error:", err);
};

// --- 4. Renderers & UI Helpers ---

function renderOrdersList() {
    if (ordersMap.size === 0) {
        ordersContainer.innerHTML = `<div class="empty-state">No orders placed yet.</div>`;
        return;
    }
    
    // Clear list
    ordersContainer.innerHTML = '';
    
    // Sort orders by timestamp (newest first)
    const sortedOrders = Array.from(ordersMap.values()).sort((a, b) => b.createdAt - a.createdAt);
    
    sortedOrders.forEach(order => {
        const item = document.createElement('div');
        item.className = `order-item ${order.orderId === activeTrackingOrderId ? 'active-tracking' : ''}`;
        item.onclick = () => selectOrderForTracking(order.orderId);
        
        const dateString = new Date(order.createdAt * 1000).toLocaleTimeString();
        
        item.innerHTML = `
            <div class="order-info">
                <h3>ID: ${order.orderId.substring(0, 8)}...</h3>
                <p>Name: ${order.customerName} | Time: ${dateString}</p>
            </div>
            <span class="order-status-badge status-${order.orderStatus.toLowerCase()}">${order.orderStatus}</span>
        `;
        ordersContainer.appendChild(item);
    });
}

function selectOrderForTracking(orderId) {
    activeTrackingOrderId = orderId;
    activeOrderIdLabel.innerText = orderId;
    
    const queryInput = document.getElementById('query-order-id');
    if (queryInput) {
        queryInput.value = orderId;
    }
    
    // Highlight selected item in list
    document.querySelectorAll('.order-item').forEach(el => el.classList.remove('active-tracking'));
    renderOrdersList();
    
    const order = ordersMap.get(orderId);
    if (order) {
        updateTrackerTimeline(order);
    } else {
        resetTimeline();
    }
}

// Reset timeline styles
function resetTimeline() {
    document.querySelectorAll('.timeline-step').forEach(step => {
        step.className = 'timeline-step';
        step.querySelector('.step-status').innerText = 'Pending';
    });
}

// Update timeline based on overall order database state
function updateTrackerTimeline(order) {
    resetTimeline();
    
    const step1 = document.getElementById('step-placed');
    const step2 = document.getElementById('step-payment-created');
    const step3 = document.getElementById('step-paid');
    const step4 = document.getElementById('step-shipped');
    
    if (order.orderStatus === 'IN_PROGRESS') {
        // Step 1: Ingested
        step1.classList.add('active', 'success');
        step1.querySelector('.step-status').innerText = 'Completed';
        
        // Mark next step as actively waiting
        step2.classList.add('active');
        step2.querySelector('.step-status').innerText = 'Processing...';
    } else if (order.orderStatus === 'COMPLETED') {
        document.querySelectorAll('.timeline-step').forEach(step => {
            step.classList.add('active', 'success');
            step.querySelector('.step-status').innerText = 'Completed';
        });
    } else if (order.orderStatus === 'FAILED') {
        document.querySelectorAll('.timeline-step').forEach(step => {
            step.classList.add('active', 'failed');
            step.querySelector('.step-status').innerText = 'Failed';
        });
    }
}

// Update specific timeline stages in real time based on message events
function handleRealTimeStepUpdates(eventType) {
    const step1 = document.getElementById('step-placed');
    const step2 = document.getElementById('step-payment-created');
    const step3 = document.getElementById('step-paid');
    const step4 = document.getElementById('step-shipped');
    
    switch (eventType) {
        case 'order.created':
            step1.className = 'timeline-step active success';
            step1.querySelector('.step-status').innerText = 'Completed';
            step2.className = 'timeline-step active';
            step2.querySelector('.step-status').innerText = 'Processing...';
            break;
            
        case 'payment.created':
            step2.className = 'timeline-step active success';
            step2.querySelector('.step-status').innerText = 'Completed';
            step3.className = 'timeline-step active';
            step3.querySelector('.step-status').innerText = 'Authorizing Card...';
            break;
            
        case 'payment.success':
            step3.className = 'timeline-step active success';
            step3.querySelector('.step-status').innerText = 'Completed';
            step4.className = 'timeline-step active';
            step4.querySelector('.step-status').innerText = 'Dispatching package...';
            break;
            
        case 'delivery.created':
            step4.className = 'timeline-step active';
            step4.querySelector('.step-status').innerText = 'Shipping...';
            break;
            
        case 'delivery.success':
            step4.className = 'timeline-step active success';
            step4.querySelector('.step-status').innerText = 'Completed';
            break;
            
        case 'payment.failure':
        case 'delivery.failure':
            document.querySelectorAll('.timeline-step').forEach(step => {
                step.className = 'timeline-step active failed';
                step.querySelector('.step-status').innerText = 'Compensated (Rollback)';
            });
            break;
    }
}

// Add an entry to the Event Bus Live Feed terminal log
function addLogEntry(eventType, text) {
    // Remove empty state message if present
    const emptyLog = logsContainer.querySelector('.empty-state');
    if (emptyLog) emptyLog.remove();
    
    const entry = document.createElement('div');
    const cleanType = eventType.replace('.', '_');
    entry.className = `log-entry ${cleanType}`;
    
    const time = new Date().toLocaleTimeString();
    entry.innerHTML = `
        <span class="log-time">[${time}]</span>
        <span class="log-event">${eventType.toUpperCase()}</span>
        <span class="log-text">${text}</span>
    `;
    
    logsContainer.appendChild(entry);
    
    // Auto-scroll to bottom of logs
    logsContainer.scrollTop = logsContainer.scrollHeight;
}

// --- 5. Inter-Service Orchestrator Query Handlers ---
document.getElementById('btn-fetch-details').addEventListener('click', function () {
    const orderId = document.getElementById('query-order-id').value.trim();
    if (!orderId) {
        alert("Please enter or select an Order ID first!");
        return;
    }
    
    addLogEntry('client.request', `🔌 Requesting combined details for Order ID: ${orderId}...`);
    
    fetch(`${ORDER_SERVICE_API}/orders/${orderId}/details`)
        .then(res => {
            if (res.status === 404) {
                throw new Error("Order ID not found in Order Service database.");
            }
            if (!res.ok) {
                throw new Error("Failed to orchestrate/fetch details from backend.");
            }
            return res.json();
        })
        .then(data => {
            addLogEntry('client.response', `✅ Successfully orchestrated details for Order ID: ${orderId}`);
            
            // Format and display JSON payload
            const jsonBox = document.getElementById('orchestrator-result-box');
            const pre = document.getElementById('orchestrator-json');
            
            pre.innerText = JSON.stringify(data, null, 4);
            jsonBox.style.display = 'block';
        })
        .catch(err => {
            addLogEntry('client.error', `❌ Orchestration Query failed: ${err.message}`);
            alert(err.message);
        });
});

document.getElementById('btn-close-orchestrator').addEventListener('click', function () {
    document.getElementById('orchestrator-result-box').style.display = 'none';
});
