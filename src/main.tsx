import React from 'react';
import {createRoot} from 'react-dom/client';
import App from './App.tsx';
import './index.css';

class RootErrorBoundary extends React.Component<
  { children: React.ReactNode },
  { hasError: boolean; errorMsg: string }
> {
  constructor(props: { children: React.ReactNode }) {
    super(props);
    this.state = { hasError: false, errorMsg: '' };
  }

  static getDerivedStateFromError(error: unknown) {
    return {
      hasError: true,
      errorMsg: error instanceof Error ? error.message : String(error),
    };
  }

  render() {
    if (this.state.hasError) {
      return (
        <div style={{ padding: 24, fontFamily: 'sans-serif', color: '#202122', background: '#fff', height: '100vh' }}>
          <h2 style={{ marginBottom: 8 }}>Reloading Likkho...</h2>
          <p style={{ fontSize: 13, color: '#54595d', marginBottom: 16 }}>{this.state.errorMsg}</p>
          <button
            onClick={() => {
              localStorage.clear();
              window.location.reload();
            }}
            style={{ padding: '8px 16px', background: '#3366cc', color: '#fff', border: 'none', fontWeight: 600 }}
          >
            Reset &amp; Open App
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}

createRoot(document.getElementById('root')!).render(
  <RootErrorBoundary>
    <App />
  </RootErrorBoundary>
);
