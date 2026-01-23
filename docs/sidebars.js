// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  tutorialSidebar: [
    'intro',
    'getting-started',
    {
      type: 'category',
      label: 'Features',
      items: [
        'features/overview',
        'features/video-player',
        'features/torrent-support',
        'features/download-manager',
        'features/media-library',
        'features/android-tv',
        'features/chromecast',
      ],
    },
    {
      type: 'category',
      label: 'Architecture',
      items: [
        'architecture/overview',
        'architecture/core-components',
        'architecture/player-architecture',
        'architecture/download-system',
      ],
    },
    'technical-stack',
    'faq',
  ],
};

module.exports = sidebars;

