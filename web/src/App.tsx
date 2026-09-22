import { Link, Route, Routes } from 'react-router-dom';
import styles from './App.module.css';
import { LoginPage } from './pages/LoginPage';
import { TeamsPage } from './pages/TeamsPage';

export function App() {
  return (
    <div className={styles.shell}>
      <nav className={styles.nav}>
        <Link to="/" className={styles.brand}>
          Javamon
        </Link>
        <Link to="/teams" className={styles.link}>
          Drużyny
        </Link>
      </nav>

      <Routes>
        <Route path="/" element={<LoginPage />} />
        <Route path="/teams" element={<TeamsPage />} />
      </Routes>
    </div>
  );
}
