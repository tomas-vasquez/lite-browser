import { useState, useEffect, useRef } from 'react';
import { Plus, X, Globe, ExternalLink, Search, LayoutGrid } from 'lucide-react';
import { SEARCH_ENGINES } from './constants';
import { NATIVE, nativeLoadTab, nativeShowTab, nativeHideTabs, nativeCloseTab, nativeShowSwitcher, nativeHideSwitcher, nativeSetFabPosition, subscribeEvents } from './browser-tabs';
import './App.css';

// Helper: extraer dominio legible
const cleanDomain = (url) => (url || '').replace(/^https?:\/\//, '');
const isNewTab = (url) => url === 'ultralite://newtab';
// Página de inicio: HTML estático servido como cualquier otra página
const HOME_URL = NATIVE ? 'https://localhost/home.html' : '/home.html';
const FAB_SIZE = 44;

const loadHistory = () => {
  try {
    const raw = localStorage.getItem('ultralite.history');
    if (raw) {
      const arr = JSON.parse(raw);
      if (Array.isArray(arr)) return arr.slice(0, 30);
    }
  } catch {
    /* noop */
  }
  return [];
};

const makeTab = (id) => ({
  id,
  ...newTabState(),
});

const newTabState = () => ({
  url: 'ultralite://newtab',
  title: 'Inicio',
  history: ['ultralite://newtab'],
  historyIndex: 0,
  isLoading: false,
  canGoBack: false,
  canGoForward: false,
});

export default function App() {
  const [theme] = useState('dark');
  const [tabs, setTabs] = useState([makeTab('tab-1')]);
  const [activeTabId, setActiveTabId] = useState('tab-1');
  const [tabSwitcherOpen, setTabSwitcherOpen] = useState(false);
  const [history, setHistory] = useState(loadHistory);
  const [fabPos, setFabPos] = useState(null);
  const [settings] = useState({
    searchEngine: 'google',
    adBlocker: true,
    javascriptEnabled: true,
    proxyMode: false,
  });

  const activeTab = tabs.find((t) => t.id === activeTabId) || tabs[0];

  // Ref para el manejo del botón atrás del sistema (evita closures obsoletos)
  const uiRef = useRef({ activeTabId, tabs });
  uiRef.current = { activeTabId, tabs };

  // Tema
  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark');
  }, [theme]);

  // Posición guardada del FAB (solo en web/dev; en Android el nativo la aplica)
  useEffect(() => {
    try {
      const saved = JSON.parse(localStorage.getItem('ultralite.fabPosition') || 'null');
      if (saved) setFabPos(saved);
    } catch {
      /* noop */
    }
  }, []);

  // Persistir posición del FAB y sincronizarla con el FAB nativo (Android)
  useEffect(() => {
    if (!fabPos) return;
    try {
      localStorage.setItem('ultralite.fabPosition', JSON.stringify(fabPos));
    } catch {
      /* noop */
    }
    if (NATIVE) nativeSetFabPosition(fabPos.x, fabPos.y);
  }, [fabPos]);

  // Persistir historial
  useEffect(() => {
    try {
      localStorage.setItem('ultralite.history', JSON.stringify(history));
    } catch {
      /* noop */
    }
  }, [history]);

  // Long-press para arrastrar el FAB de React (solo web/dev)
  const fabDragRef = useRef({ timer: null, dragging: false, startX: 0, startY: 0 });
  const suppressFabClickRef = useRef(false);

  const clearFabTimer = () => {
    const d = fabDragRef.current;
    if (d.timer) {
      clearTimeout(d.timer);
      d.timer = null;
    }
  };

  const handleFabPointerMove = (e) => {
    const d = fabDragRef.current;
    if (!d.dragging) {
      if (Math.abs(e.clientX - d.startX) > 8 || Math.abs(e.clientY - d.startY) > 8) clearFabTimer();
      return;
    }
    e.preventDefault();
    setFabPos({
      x: Math.max(0, Math.min(window.innerWidth - FAB_SIZE, Math.round(e.clientX - FAB_SIZE / 2))),
      y: Math.max(0, Math.min(window.innerHeight - FAB_SIZE, Math.round(e.clientY - FAB_SIZE / 2))),
    });
  };

  const handleFabPointerUp = () => {
    const wasDragging = fabDragRef.current.dragging;
    clearFabTimer();
    window.removeEventListener('pointermove', handleFabPointerMove);
    window.removeEventListener('pointerup', handleFabPointerUp);
    window.removeEventListener('pointercancel', handleFabPointerUp);
    fabDragRef.current.dragging = false;
    suppressFabClickRef.current = wasDragging;
  };

  const handleFabPointerDown = (e) => {
    clearFabTimer();
    fabDragRef.current = {
      timer: null,
      dragging: false,
      startX: e.clientX,
      startY: e.clientY,
    };
    fabDragRef.current.timer = setTimeout(() => {
      fabDragRef.current.dragging = true;
    }, 400);
    window.addEventListener('pointermove', handleFabPointerMove);
    window.addEventListener('pointerup', handleFabPointerUp);
    window.addEventListener('pointercancel', handleFabPointerUp);
  };

  const handleFabClick = () => {
    if (suppressFabClickRef.current) {
      suppressFabClickRef.current = false;
      return;
    }
    openTabSwitcher();
  };

  const pushWindowHistory = () => {
    try { window.history.pushState({ app: true }, ''); } catch { /* noop */ }
  };

  const updateTab = (tabId, updates) => {
    setTabs((prev) => prev.map((t) => (t.id === tabId ? { ...t, ...updates } : t)));
  };

  const recordHistory = (url, title) => {
    if (isNewTab(url)) return;
    setHistory((prev) => {
      if (prev[0] && prev[0].url === url) return prev;
      return [
        { title: title || url, url, timestamp: new Date().toLocaleTimeString() },
        ...prev,
      ].slice(0, 30);
    });
  };

  const pushToTabHistory = (tabId, url, title, { updateWindow = true } = {}) => {
    const tab = tabs.find((t) => t.id === tabId);
    if (!tab) return;
    const newHistory = [...tab.history.slice(0, tab.historyIndex + 1), url];
    const newIndex = newHistory.length - 1;

    updateTab(tabId, {
      url,
      title: isNewTab(url) ? 'Inicio' : title || url,
      history: newHistory,
      historyIndex: newIndex,
      canGoBack: newIndex > 0,
      canGoForward: false,
      isLoading: !isNewTab(url),
    });

    recordHistory(url, title);

    if (updateWindow) pushWindowHistory();

    if (!isNewTab(url)) {
      setTimeout(() => updateTab(tabId, { isLoading: false }), 250);
    }
  };

  // Navegación (URL -> página)
  const navigateTo = (rawUrl) => {
    let finalUrl = (rawUrl || '').trim();
    if (!finalUrl) return;

    if (isNewTab(finalUrl)) {
      updateTab(activeTabId, newTabState());
      if (NATIVE) nativeHideTabs();
      return;
    }

    let targetUrl = finalUrl;
    if (!finalUrl.includes('://')) {
      const isDomain =
        /^[a-zA-Z0-9][-a-zA-Z0-9]*(\.[a-zA-Z0-9][-a-zA-Z0-9]*)+/.test(finalUrl) ||
        finalUrl.startsWith('localhost');
      if (isDomain) {
        targetUrl = 'https://' + finalUrl;
      } else {
        const engine = SEARCH_ENGINES[settings.searchEngine] || SEARCH_ENGINES.duckduckgo;
        targetUrl = engine.urlTemplate + encodeURIComponent(finalUrl);
      }
    } else if (finalUrl.startsWith('http://') && settings.proxyMode) {
      targetUrl = finalUrl.replace('http://', 'https://');
    }

    if (NATIVE) {
      nativeLoadTab(activeTabId, targetUrl);
      updateTab(activeTabId, {
        url: targetUrl,
        title: cleanDomain(targetUrl),
        history: ['ultralite://newtab', targetUrl],
        historyIndex: 1,
        isLoading: true,
        canGoBack: true,
        canGoForward: false,
      });
      recordHistory(targetUrl, targetUrl);
      nativeShowTab(activeTabId);
      return;
    }

    pushToTabHistory(activeTabId, targetUrl, targetUrl);
  };

  // Atrás interno de una pestaña (usado por el botón atrás del sistema)
  const goBackInternal = (tab) => {
    if (!tab || tab.historyIndex <= 0) return;
    const newIndex = tab.historyIndex - 1;
    const targetUrl = tab.history[newIndex];
    updateTab(tab.id, {
      url: targetUrl,
      title: isNewTab(targetUrl) ? 'Inicio' : targetUrl,
      historyIndex: newIndex,
      canGoBack: newIndex > 0,
      canGoForward: true,
      isLoading: !isNewTab(targetUrl),
    });
    if (!isNewTab(targetUrl)) {
      setTimeout(() => updateTab(tab.id, { isLoading: false }), 250);
    }
  };

  // Selector de pestañas: en la app nativa abre el bottom sheet nativo de Android
  const openTabSwitcher = () => {
    if (!NATIVE) {
      setTabSwitcherOpen(true);
      return;
    }
    const { tabs: list, activeTabId: activeId } = uiRef.current;
    nativeShowSwitcher(
      list.map((t) => ({
        id: t.id,
        title: isNewTab(t.url) ? 'Nueva pestaña' : t.title || cleanDomain(t.url),
        active: t.id === activeId,
        isNew: isNewTab(t.url),
      }))
    );
  };

  const selectTab = (id) => {
    setActiveTabId(id);
    setTabSwitcherOpen(false);
    if (NATIVE) {
      nativeHideSwitcher();
      const t = uiRef.current.tabs.find((x) => x.id === id);
      if (t && !isNewTab(t.url)) nativeShowTab(id);
      else nativeHideTabs();
    }
  };

  // Botón atrás del sistema Android (WebView): popstate
  useEffect(() => {
    if (NATIVE) return;
    const onPopState = () => {
      const { activeTabId: id, tabs: list } = uiRef.current;
      const t = list.find((x) => x.id === id) || list[0];
      if (!t) return;
      if (t.canGoBack) goBackInternal(t);
    };
    window.addEventListener('popstate', onPopState);
    pushWindowHistory(); // entrada inicial para poder capturar atrás
    return () => window.removeEventListener('popstate', onPopState);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // La página de inicio (home.html) pide navegación vía postMessage cuando está en un iframe
  useEffect(() => {
    const onMessage = (e) => {
      if (e?.data?.type === 'ultralite:navigate' && typeof e.data.url === 'string') {
        navigateTo(e.data.url);
      }
    };
    window.addEventListener('message', onMessage);
    return () => window.removeEventListener('message', onMessage);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Eventos del plugin nativo (páginas nativas por pestaña)
  useEffect(() => {
    return subscribeEvents((ev, data) => {
      const tabId = data && data.tabId;
      switch (ev) {
        case 'onUrlChange':
          updateTab(tabId, { url: data.url, isLoading: true });
          recordHistory(data.url, data.url);
          break;
        case 'onTitleChange':
          updateTab(tabId, { title: data.title || data.url });
          break;
        case 'onProgress':
          updateTab(tabId, { isLoading: data.progress < 100 });
          break;
        case 'onFabTap':
          openTabSwitcher();
          break;
        case 'onFabPosition':
          setFabPos({ x: data.x, y: data.y });
          break;
        case 'onTabBackHome':
          updateTab(tabId, newTabState());
          break;
        case 'onSwitcherSelect':
          selectTab(tabId);
          break;
        case 'onSwitcherNew':
          createNewTab();
          break;
        case 'onSwitcherCloseTab':
          closeTab(tabId);
          break;
        default:
          break;
      }
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Pestañas
  const createNewTab = () => {
    const newId = 'tab-' + Date.now();
    setTabs((prev) => [...prev, makeTab(newId)]);
    setActiveTabId(newId);
    if (NATIVE) nativeHideTabs();
    else pushWindowHistory();
  };

  const closeTab = (tabId) => {
    const { tabs: list, activeTabId: activeId } = uiRef.current;
    if (list.length <= 1) return;
    if (NATIVE) nativeCloseTab(tabId);
    const tabIndex = list.findIndex((t) => t.id === tabId);
    const newTabs = list.filter((t) => t.id !== tabId);
    setTabs(newTabs);
    if (activeId === tabId) {
      const next = newTabs[Math.max(0, tabIndex - 1)];
      setActiveTabId(next.id);
      if (NATIVE && !isNewTab(next.url)) nativeShowTab(next.id);
    }
  };

  return (
    <div className={`browser-container ${theme}`}>
      <div className="viewport">
        {activeTab?.isLoading && (
          <div className="loading-bar">
            <div className="loading-fill" />
          </div>
        )}

        {!NATIVE && (
          <button
            type="button"
            className="fab-tabs"
            style={fabPos ? { left: fabPos.x, top: fabPos.y, right: 'auto', bottom: 'auto' } : undefined}
            onPointerDown={handleFabPointerDown}
            onClick={handleFabClick}
            aria-label="Cambiar de pestaña"
            title="Pestañas abiertas"
          >
            <LayoutGrid size={20} />
          </button>
        )}

        <div className="page-stack">
          {tabs.map((tab) =>
            !NATIVE && !isNewTab(tab.url) ? (
              <iframe
                key={tab.id}
                src={tab.url}
                title={tab.title}
                className={`web-iframe ${tab.id === activeTabId ? '' : 'hidden'}`}
                sandbox={
                  settings.javascriptEnabled
                    ? 'allow-scripts allow-same-origin allow-forms allow-popups'
                    : 'allow-same-origin'
                }
                onError={() => console.warn('Iframe load blocked or failed')}
              />
            ) : null
          )}

          {isNewTab(activeTab?.url) && (
            <iframe
              key={activeTab.id}
              src={HOME_URL}
              title="Inicio"
              className="web-iframe"
              sandbox="allow-scripts allow-same-origin allow-forms"
            />
          )}

          {!NATIVE && !isNewTab(activeTab?.url) && (
            <div className="fallback-banner">
              <div className="fallback-inner">
                <Globe size={26} className="c-text-violet" />
                <h3>Mostrando {cleanDomain(activeTab?.url)}</h3>
                <p>
                  Algunos sitios bloquean su contenido dentro de iframes por
                  políticas de seguridad X-Frame-Options.
                </p>
                <div className="fallback-actions">
                  <a
                    href={activeTab?.url}
                    target="_blank"
                    rel="noreferrer"
                    className="btn-open-external"
                  >
                    <ExternalLink size={14} /> Abrir externo
                  </a>
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={() => navigateTo('ultralite://newtab')}
                  >
                    Volver a inicio
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>

        {tabSwitcherOpen && (
          <TabSwitcher
            tabs={tabs}
            activeTabId={activeTabId}
            onSelect={(id) => {
              selectTab(id);
            }}
            onCreate={() => {
              createNewTab();
              setTabSwitcherOpen(false);
            }}
            onClose={closeTab}
            onCloseSwitcher={() => setTabSwitcherOpen(false)}
          />
        )}
      </div>
    </div>
  );
}

function TabSwitcher({ tabs, activeTabId, onSelect, onCreate, onClose, onCloseSwitcher }) {
  return (
    <div className="switcher-backdrop" onClick={onCloseSwitcher}>
      <div className="switcher-sheet" onClick={(e) => e.stopPropagation()}>
        <div className="switcher-head">
          <h3>Pestañas ({tabs.length})</h3>
          <button type="button" className="icon-btn" onClick={onCloseSwitcher} aria-label="Cerrar">
            <X size={18} />
          </button>
        </div>
        <div className="switcher-list">
          {tabs.map((tab) => (
            <div
              key={tab.id}
              role="button"
              tabIndex={0}
              className={`switcher-item ${tab.id === activeTabId ? 'active' : ''}`}
              onClick={() => onSelect(tab.id)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  onSelect(tab.id);
                }
              }}
            >
              <span className="switcher-item-icon">
                {tab.isLoading ? (
                  <span className="spinner-mini" />
                ) : isNewTab(tab.url) ? (
                  <Search size={16} className="c-text-violet" />
                ) : (
                  <Globe size={16} className="c-text-slate" />
                )}
              </span>
              <span className="switcher-item-title">
                {isNewTab(tab.url) ? 'Nueva pestaña' : cleanDomain(tab.title)}
              </span>
              <button
                type="button"
                className="switcher-item-close"
                aria-label="Cerrar pestaña"
                onClick={(e) => {
                  e.stopPropagation();
                  onClose(tab.id);
                }}
              >
                <X size={15} />
              </button>
            </div>
          ))}
        </div>
        <button type="button" className="switcher-new" onClick={onCreate}>
          <Plus size={16} /> Nueva pestaña
        </button>
      </div>
    </div>
  );
}