import { useEffect, useState } from "react";
import "./SearchBar.css";

export function SearchBar({ onSearch }: { onSearch: (query: string) => void }) {
  const [value, setValue] = useState("");

  useEffect(() => {
    const timeout = setTimeout(() => onSearch(value), 300);
    return () => clearTimeout(timeout);
  }, [value, onSearch]);

  return (
    <div className="search-bar">
      <input
        type="search"
        placeholder="Search notebooks by title or content…"
        value={value}
        onChange={(e) => setValue(e.target.value)}
      />
    </div>
  );
}
