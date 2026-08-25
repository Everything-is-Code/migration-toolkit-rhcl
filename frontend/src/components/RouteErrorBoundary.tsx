import React, { Component, ErrorInfo, ReactNode } from 'react';
import i18next from 'i18next';
import styles from '../App.module.css';

interface EBState { hasError: boolean; message: string; path: string; }

class RouteErrorBoundary extends Component<{ children: ReactNode }, EBState> {
  state: EBState = { hasError: false, message: '', path: '' };
  static getDerivedStateFromError(e: Error): Partial<EBState> {
    return { hasError: true, message: e.message };
  }
  componentDidCatch(e: Error, info: ErrorInfo) {
    console.error('[RouteErrorBoundary]', e, info);
    this.setState({ path: window.location.pathname });
  }
  componentDidUpdate(_: unknown, prev: EBState) {
    if (prev.path && window.location.pathname !== prev.path) {
      this.setState({ hasError: false, message: '', path: '' });
    }
  }
  render() {
    if (this.state.hasError) {
      return (
        <div style={{ padding: '32px' }}>
          <div className={styles.errorBanner}>
            <p className={styles.errorTitle}>
              {i18next.t('app.errorTitle')}
            </p>
            <p className={styles.errorMessage}>
              {this.state.message}
            </p>
            <button
              onClick={() => this.setState({ hasError: false, message: '', path: '' })}
              className={styles.errorRetry}
            >
              {i18next.t('app.btnRetry')}
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

export default RouteErrorBoundary;
