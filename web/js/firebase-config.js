// Firebase configuration for the TEAMSYNC web client (C3).
// This is public client config, not a secret: access is enforced by
// Firebase Security Rules (firestore.rules, storage.rules).
// Loaded from the Firebase CDN, so no `npm install firebase` is needed.
import { initializeApp } from "https://www.gstatic.com/firebasejs/13.4.0/firebase-app.js";

export const firebaseConfig = {
  apiKey: "AIzaSyC-15Vo3aR8oQ28HmIKhAr84BlvkaiM9xo",
  authDomain: "teamsync-it71.firebaseapp.com",
  projectId: "teamsync-it71",
  storageBucket: "teamsync-it71.firebasestorage.app",
  messagingSenderId: "258375171826",
  appId: "1:258375171826:web:444ff31f02d8658565da6b"
};

export const app = initializeApp(firebaseConfig);
