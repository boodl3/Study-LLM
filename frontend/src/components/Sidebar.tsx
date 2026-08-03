import { Link, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { IconButton } from "./IconButton";
import { HomeIcon, PowerIcon, SettingsIcon, UserCircleIcon } from "./SidebarIcons";
import "./Sidebar.css";

export function Sidebar() {
  const { isAuthenticated, username, logout } = useAuth();
  const { pathname } = useLocation();

  return (
    <aside className="sidebar">
      <Link to="/" className="sidebar__brand">
        Study LLM
      </Link>

      {isAuthenticated && (
        <nav className="sidebar__nav">
          <Link
            to="/"
            className={`sidebar__nav-item${pathname === "/" ? " sidebar__nav-item--active" : ""}`}
          >
            <HomeIcon /> Home
          </Link>
          <Link
            to="/settings"
            className={`sidebar__nav-item${pathname === "/settings" ? " sidebar__nav-item--active" : ""}`}
          >
            <SettingsIcon /> Settings
          </Link>
        </nav>
      )}

      <div className="sidebar__footer">
        {isAuthenticated ? (
          <>
            <span className="sidebar__user" title={username ?? undefined}>
              <UserCircleIcon />
              <span className="sidebar__username">{username}</span>
            </span>
            <IconButton label="Log out" onClick={logout}>
              <PowerIcon />
            </IconButton>
          </>
        ) : (
          <Link to="/login" className="sidebar__login-link">
            <UserCircleIcon /> Log in
          </Link>
        )}
      </div>
    </aside>
  );
}
