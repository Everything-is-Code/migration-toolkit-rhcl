import React from 'react';
import { BrowserRouter } from 'react-router-dom';
import { AppStateProvider } from './components/AppStateContext';
import AppLayout from './components/AppLayout';

const App: React.FC = () => (
  <BrowserRouter>
    <AppStateProvider>
      <AppLayout />
    </AppStateProvider>
  </BrowserRouter>
);

export default App;
