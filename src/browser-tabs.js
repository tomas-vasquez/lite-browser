import { Capacitor } from '@capacitor/core';

// true dentro de la app Capacitor (Android), false en dev web
export const NATIVE = Capacitor.isNativePlatform();

// Proxy al plugin Java TabWebViewPlugin (null en web)
export const Tabs = NATIVE ? Capacitor.registerPlugin('TabWebView') : null;

const EVENTS = [
  'onUrlChange',
  'onTitleChange',
  'onProgress',
  'onFabTap',
  'onFabPosition',
  'onTabBackHome',
  'onTabShown',
  'onTabsHidden',
  'onSwitcherSelect',
  'onSwitcherNew',
  'onSwitcherCloseTab',
];

export function subscribeEvents(handler) {
  if (!Tabs) return () => {};
  const handles = [];
  EVENTS.forEach((ev) => {
    Tabs.addListener(ev, (data) => handler(ev, data)).then((h) => handles.push(h));
  });
  return () => handles.forEach((h) => h.remove());
}

export const nativeCreateTab = (id, url) => Tabs && Tabs.createTab({ id, url });
export const nativeShowTab = (id) => Tabs && Tabs.showTab({ id });
export const nativeHideTabs = () => Tabs && Tabs.hideTabs();
export const nativeLoadTab = (id, url) => Tabs && Tabs.loadTab({ id, url });
export const nativeCloseTab = (id) => Tabs && Tabs.closeTab({ id });
export const nativeShowSwitcher = (tabs) => Tabs && Tabs.showSwitcher({ tabs });
export const nativeHideSwitcher = () => Tabs && Tabs.hideSwitcher();
export const nativeSetFabPosition = (x, y) => Tabs && Tabs.setFabPosition({ x, y });