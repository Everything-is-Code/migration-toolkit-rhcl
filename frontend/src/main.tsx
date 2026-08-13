import React from 'react';
import ReactDOM from 'react-dom/client';
import '@patternfly/react-core/dist/styles/base.css';
import './i18n';
import App from './App';

const rootEl = document.getElementById('root');

if (!rootEl) {
  document.body.innerHTML =
    '<div style="padding:40px;font-family:monospace;color:#c9190b">' +
    '<h2>Startup Error: #root element not found</h2>' +
    '<button onclick="location.reload()" style="margin-top:16px;padding:8px 20px;cursor:pointer">Reload</button>' +
    '</div>';
} else {
  ReactDOM.createRoot(rootEl).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>
  );
}
