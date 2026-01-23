import React from 'react';
import ComponentCreator from '@docusaurus/ComponentCreator';

export default [
  {
    path: '/__docusaurus/debug',
    component: ComponentCreator('/__docusaurus/debug', '72c'),
    exact: true
  },
  {
    path: '/__docusaurus/debug/config',
    component: ComponentCreator('/__docusaurus/debug/config', '508'),
    exact: true
  },
  {
    path: '/__docusaurus/debug/content',
    component: ComponentCreator('/__docusaurus/debug/content', '8fa'),
    exact: true
  },
  {
    path: '/__docusaurus/debug/globalData',
    component: ComponentCreator('/__docusaurus/debug/globalData', 'c44'),
    exact: true
  },
  {
    path: '/__docusaurus/debug/metadata',
    component: ComponentCreator('/__docusaurus/debug/metadata', 'd05'),
    exact: true
  },
  {
    path: '/__docusaurus/debug/registry',
    component: ComponentCreator('/__docusaurus/debug/registry', '356'),
    exact: true
  },
  {
    path: '/__docusaurus/debug/routes',
    component: ComponentCreator('/__docusaurus/debug/routes', '388'),
    exact: true
  },
  {
    path: '/docs',
    component: ComponentCreator('/docs', 'ffa'),
    routes: [
      {
        path: '/docs',
        component: ComponentCreator('/docs', '392'),
        routes: [
          {
            path: '/docs',
            component: ComponentCreator('/docs', '1fa'),
            routes: [
              {
                path: '/docs/architecture/core-components',
                component: ComponentCreator('/docs/architecture/core-components', '339'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/docs/architecture/download-system',
                component: ComponentCreator('/docs/architecture/download-system', '886'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/docs/architecture/overview',
                component: ComponentCreator('/docs/architecture/overview', '20a'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/docs/architecture/player-architecture',
                component: ComponentCreator('/docs/architecture/player-architecture', 'ffe'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/docs/faq',
                component: ComponentCreator('/docs/faq', 'e79'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/docs/features/android-tv',
                component: ComponentCreator('/docs/features/android-tv', 'f83'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/docs/features/chromecast',
                component: ComponentCreator('/docs/features/chromecast', '123'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/docs/features/download-manager',
                component: ComponentCreator('/docs/features/download-manager', '5a3'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/docs/features/media-library',
                component: ComponentCreator('/docs/features/media-library', '4a1'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/docs/features/overview',
                component: ComponentCreator('/docs/features/overview', 'a8b'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/docs/features/torrent-support',
                component: ComponentCreator('/docs/features/torrent-support', 'ba6'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/docs/features/video-player',
                component: ComponentCreator('/docs/features/video-player', '0d1'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/docs/getting-started',
                component: ComponentCreator('/docs/getting-started', 'a24'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/docs/intro',
                component: ComponentCreator('/docs/intro', 'aed'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/docs/technical-stack',
                component: ComponentCreator('/docs/technical-stack', 'f83'),
                exact: true,
                sidebar: "tutorialSidebar"
              },
              {
                path: '/docs/theme-guide',
                component: ComponentCreator('/docs/theme-guide', 'c2d'),
                exact: true
              }
            ]
          }
        ]
      }
    ]
  },
  {
    path: '/',
    component: ComponentCreator('/', '956'),
    exact: true
  },
  {
    path: '*',
    component: ComponentCreator('*'),
  },
];
