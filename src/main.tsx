import {createRoot} from 'react-dom/client';
import App from './App.tsx';
import './index.css';

// Unregister any stale dev service workers that could cause a blank white preview screen
if (typeof navigator !== 'undefined' && 'serviceWorker' in navigator) {
  navigator.serviceWorker.getRegistrations().then((registrations) => {
    for (const registration of registrations) {
      registration.unregister();
    }
  }).catch(() => {});
}

createRoot(document.getElementById('root')!).render(<App />);
