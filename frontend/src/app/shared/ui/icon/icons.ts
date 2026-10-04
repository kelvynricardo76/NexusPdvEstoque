/**
 * Registro de ícones outline do Nexus Design System (grade 24×24, traço em currentColor).
 * Ícones são dados — não HTML — para evitar innerHTML e manter tudo tipado.
 */
export type IconShape =
  | { readonly kind: 'path'; readonly d: string }
  | { readonly kind: 'circle'; readonly cx: number; readonly cy: number; readonly r: number }
  | {
      readonly kind: 'rect';
      readonly x: number;
      readonly y: number;
      readonly width: number;
      readonly height: number;
      readonly rx: number;
    };

const path = (d: string): IconShape => ({ kind: 'path', d });
const circle = (cx: number, cy: number, r: number): IconShape => ({ kind: 'circle', cx, cy, r });
const rect = (x: number, y: number, width: number, height: number, rx: number): IconShape => ({
  kind: 'rect',
  x,
  y,
  width,
  height,
  rx,
});

export const NX_ICONS = {
  mail: [rect(2, 4, 20, 16, 2), path('m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7')],
  lock: [rect(3, 11, 18, 11, 2), path('M7 11V7a5 5 0 0 1 10 0v4')],
  eye: [
    path('M2.06 12.35a1 1 0 0 1 0-.7 10.75 10.75 0 0 1 19.88 0 1 1 0 0 1 0 .7 10.75 10.75 0 0 1-19.88 0'),
    circle(12, 12, 3),
  ],
  'eye-off': [
    path('M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68'),
    path('M6.61 6.61A13.53 13.53 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61'),
    path('M14.12 14.12a3 3 0 1 1-4.24-4.24'),
    path('m2 2 20 20'),
  ],
  cart: [
    circle(8, 21, 1),
    circle(19, 21, 1),
    path('M2.05 2.05h2l2.66 12.42a2 2 0 0 0 2 1.58h9.78a2 2 0 0 0 1.95-1.57l1.65-7.43H5.12'),
  ],
  package: [
    path(
      'M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z',
    ),
    path('M12 22V12'),
    path('m3.3 7 7.7 4.73a2 2 0 0 0 2 0L20.7 7'),
  ],
  'bar-chart': [path('M3 3v16a2 2 0 0 0 2 2h16'), path('M18 17V9'), path('M13 17V5'), path('M8 17v-3')],
  'shield-check': [
    path(
      'M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z',
    ),
    path('m9 12 2 2 4-4'),
  ],
  'alert-circle': [circle(12, 12, 10), path('M12 8v4'), path('M12 16h.01')],
  'alert-triangle': [
    path('m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3'),
    path('M12 9v4'),
    path('M12 17h.01'),
  ],
  info: [circle(12, 12, 10), path('M12 16v-4'), path('M12 8h.01')],
  check: [path('M20 6 9 17l-5-5')],
  'check-circle': [circle(12, 12, 10), path('m9 12 2 2 4-4')],
  'x-circle': [circle(12, 12, 10), path('m15 9-6 6'), path('m9 9 6 6')],
  'arrow-right': [path('M5 12h14'), path('m12 5 7 7-7 7')],
  'arrow-left': [path('m12 19-7-7 7-7'), path('M19 12H5')],
  home: [path('m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z'), path('M9 22V12h6v10')],
  receipt: [
    path('M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z'),
    path('M16 8h-6a2 2 0 1 0 0 4h4a2 2 0 1 1 0 4H8'),
    path('M12 17.5v-11'),
  ],
  tag: [
    path(
      'M12.59 2.59A2 2 0 0 0 11.17 2H4a2 2 0 0 0-2 2v7.17a2 2 0 0 0 .59 1.42l8.7 8.7a2.43 2.43 0 0 0 3.42 0l6.58-6.58a2.43 2.43 0 0 0 0-3.42z',
    ),
    circle(7.5, 7.5, 0.5),
  ],
  folder: [
    path(
      'M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z',
    ),
  ],
  users: [
    path('M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2'),
    circle(9, 7, 4),
    path('M22 21v-2a4 4 0 0 0-3-3.87'),
    path('M16 3.13a4 4 0 0 1 0 7.75'),
  ],
  user: [circle(12, 8, 5), path('M20 21a8 8 0 0 0-16 0')],
  'user-cog': [
    circle(10, 8, 5),
    path('M2 21a8 8 0 0 1 12-6.93'),
    circle(18, 18, 3),
    path('M18 14v1'),
    path('M18 21v1'),
    path('M22 18h-1'),
    path('M15 18h-1'),
  ],
  truck: [
    path('M14 18V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v11a1 1 0 0 0 1 1h2'),
    path('M15 18H9'),
    path('M19 18h2a1 1 0 0 0 1-1v-3.65a1 1 0 0 0-.22-.62l-3.48-4.35A1 1 0 0 0 17.52 8H14'),
    circle(17, 18, 2),
    circle(7, 18, 2),
  ],
  wallet: [
    path('M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1'),
    path('M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4'),
  ],
  upload: [path('M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4'), path('m17 8-5-5-5 5'), path('M12 3v12')],
  download: [path('M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4'), path('m7 10 5 5 5-5'), path('M12 15V3')],
  'file-text': [
    path('M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z'),
    path('M14 2v4a2 2 0 0 0 2 2h4'),
    path('M10 9H8'),
    path('M16 13H8'),
    path('M16 17H8'),
  ],
  settings: [
    path(
      'M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z',
    ),
    circle(12, 12, 3),
  ],
  search: [circle(11, 11, 8), path('m21 21-4.3-4.3')],
  bell: [path('M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9'), path('M10.3 21a1.94 1.94 0 0 0 3.4 0')],
  'chevron-down': [path('m6 9 6 6 6-6')],
  'chevron-left': [path('m15 18-6-6 6-6')],
  'chevron-right': [path('m9 18 6-6-6-6')],
  'log-out': [path('M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4'), path('m16 17 5-5-5-5'), path('M21 12H9')],
  plus: [path('M5 12h14'), path('M12 5v14')],
  minus: [path('M5 12h14')],
  edit: [
    path(
      'M21.17 6.81a1 1 0 0 0-3.99-3.99L3.84 16.17a2 2 0 0 0-.5.83l-1.32 4.35a.5.5 0 0 0 .62.62l4.35-1.32a2 2 0 0 0 .83-.5z',
    ),
    path('m15 5 4 4'),
  ],
  trash: [
    path('M3 6h18'),
    path('M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6'),
    path('M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2'),
  ],
  x: [path('M18 6 6 18'), path('m6 6 12 12')],
  filter: [path('M22 3H2l8 9.46V19l4 2v-8.54L22 3z')],
  'trending-up': [path('M22 7 13.5 15.5 8.5 10.5 2 17'), path('M16 7h6v6')],
  'trending-down': [path('M22 17 13.5 8.5 8.5 13.5 2 7'), path('M16 17h6v-6')],
  calendar: [rect(3, 4, 18, 18, 2), path('M16 2v4'), path('M8 2v4'), path('M3 10h18')],
  building: [
    rect(4, 2, 16, 20, 2),
    path('M9 22v-4h6v4'),
    path('M8 6h.01'),
    path('M16 6h.01'),
    path('M12 6h.01'),
    path('M12 10h.01'),
    path('M12 14h.01'),
    path('M16 10h.01'),
    path('M16 14h.01'),
    path('M8 10h.01'),
    path('M8 14h.01'),
  ],
  layers: [
    path('M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83Z'),
    path('m22 17.65-9.17 4.16a2 2 0 0 1-1.66 0L2 17.65'),
    path('m22 12.65-9.17 4.16a2 2 0 0 1-1.66 0L2 12.65'),
  ],
  toggle: [rect(2, 6, 20, 12, 6), circle(16, 12, 2)],
  repeat: [
    path('m17 2 4 4-4 4'),
    path('M3 11v-1a4 4 0 0 1 4-4h14'),
    path('m7 22-4-4 4-4'),
    path('M21 13v1a4 4 0 0 1-4 4H3'),
  ],
  'credit-card': [rect(2, 5, 20, 14, 2), path('M2 10h20')],
  menu: [path('M4 6h16'), path('M4 12h16'), path('M4 18h16')],
  printer: [
    path('M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2'),
    path('M6 9V3a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v6'),
    rect(6, 14, 12, 8, 1),
  ],
  scan: [
    path('M3 7V5a2 2 0 0 1 2-2h2'),
    path('M17 3h2a2 2 0 0 1 2 2v2'),
    path('M21 17v2a2 2 0 0 1-2 2h-2'),
    path('M7 21H5a2 2 0 0 1-2-2v-2'),
    path('M8 7v10'),
    path('M12 7v10'),
    path('M16 7v10'),
  ],
  refresh: [
    path('M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8'),
    path('M21 3v5h-5'),
    path('M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16'),
    path('M8 16H3v5'),
  ],
  dollar: [path('M12 2v20'), path('M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6')],
  clock: [circle(12, 12, 10), path('M12 6v6l4 2')],
  sparkles: [
    path(
      'M9.94 15.5A2 2 0 0 0 8.5 14.06l-6.14-1.58a.5.5 0 0 1 0-.96L8.5 9.94A2 2 0 0 0 9.94 8.5l1.58-6.14a.5.5 0 0 1 .96 0l1.58 6.14a2 2 0 0 0 1.44 1.44l6.14 1.58a.5.5 0 0 1 0 .96l-6.14 1.58a2 2 0 0 0-1.44 1.44l-1.58 6.14a.5.5 0 0 1-.96 0z',
    ),
  ],
  more: [circle(12, 12, 1), circle(19, 12, 1), circle(5, 12, 1)],
  key: [
    path('m15.5 7.5 2.3 2.3a1 1 0 0 0 1.4 0l2.1-2.1a1 1 0 0 0 0-1.4L19 4'),
    path('m21 2-9.6 9.6'),
    circle(7.5, 15.5, 5.5),
  ],
  power: [path('M12 2v10'), path('M18.4 6.6a9 9 0 1 1-12.77.04')],
  undo: [path('M9 14 4 9l5-5'), path('M4 9h10.5a5.5 5.5 0 0 1 0 11H11')],
  store: [
    path('m2 7 4.41-4.41A2 2 0 0 1 7.83 2h8.34a2 2 0 0 1 1.42.59L22 7'),
    path('M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8'),
    path('M15 22v-4a2 2 0 0 0-2-2h-2a2 2 0 0 0-2 2v4'),
    path('M2 7h20'),
  ],
} as const satisfies Record<string, readonly IconShape[]>;

export type IconName = keyof typeof NX_ICONS;
