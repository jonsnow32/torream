export default {
  title: "CSPlayer Docs",
  description: "CSPlayer Documentation",
  themeConfig: {
    nav: [
      { text: "Home", link: "/" },
      { text: "Guide", link: "/guide/" },
      { text: "Privacy", link: "/privacy/" },
    ],
    sidebar: {
      "/guide/": [
        {
          text: "Guide",
          items: [
            { text: "Introduction", link: "/guide/" },
            { text: "Getting Started", link: "/guide/getting-started" }
          ]
        }
      ]
    }
  }
}
