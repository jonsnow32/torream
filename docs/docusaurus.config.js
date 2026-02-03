// @ts-check
// Note: type annotations allow type checking and IDEs autocompletion

const darkCodeTheme = require('prism-react-renderer').themes.dracula;

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'ZippyPlayer',
  tagline: 'Advanced Android Video Player with Torrent & HLS Streaming',
  favicon: 'img/logo.webp',

  // Set the production url of your site here
  url: 'https://your-docusaurus-site.example.com',
  // Set the /<baseUrl>/ pathname under which your site is served
  baseUrl: '/',

  // GitHub pages deployment config.
  organizationName: 'your-org',
  projectName: 'zippyplayer',

  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'throw',

  // Internationalization
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: require.resolve('./sidebars.js'),
          editUrl: 'https://github.com/your-org/zippyplayer/tree/main/docs/',
          showLastUpdateTime: true,
          showLastUpdateAuthor: true,
        },
        blog: false,
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      }),
    ],
  ],

  plugins: [
    async function tailwindcssPlugin() {
      return {
        name: 'docusaurus-tailwindcss',
        configurePostCss(postcssOptions) {
          postcssOptions.plugins.push(require('tailwindcss'));
          postcssOptions.plugins.push(require('autoprefixer'));
          return postcssOptions;
        },
      };
    },
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      image: 'img/logo.webp',
      colorMode: {
        defaultMode: 'dark',
        disableSwitch: true,
      },
      navbar: {
        title: 'ZippyPlayer',
        logo: {
          alt: 'ZippyPlayer Logo',
          src: 'img/logo.png',
        },
        style: 'dark',
        hideOnScroll: false,
        items: [
          {
            type: 'docSidebar',
            sidebarId: 'tutorialSidebar',
            position: 'left',
            label: 'Documentation',
          },
          {
            href: 'https://zippyplayer.example.com/download',
            label: 'Download',
            position: 'right',
          },
          {
            href: 'https://github.com/your-org/zippyplayer',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Docs',
            items: [
              {
                label: 'Getting Started',
                to: '/docs/getting-started',
              },
              {
                label: 'Features',
                to: '/docs/features',
              },
            ],
          },
          {
            title: 'Community',
            items: [
              {
                label: 'GitHub',
                href: 'https://github.com/your-org/zippyplayer',
              },
            ],
          },
          {
            title: 'More',
            items: [
              {
                label: 'Technical Stack',
                to: '/docs/technical-stack',
              },
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} ZippyPlayer. Built with Docusaurus.`,
      },
      prism: {
        theme: darkCodeTheme,
        additionalLanguages: ['java', 'kotlin', 'markup'],
      },
    }),
};

module.exports = config;

