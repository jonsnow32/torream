import useDocusaurusContext from '@docusaurus/useDocusaurusContext'
import Layout from '@theme/Layout'
import Link from '@docusaurus/Link'
import useBaseUrl from '@docusaurus/useBaseUrl'
import styles from './index.module.css'

type Release = {
  version: string
  changes: string[]
}

// Keep in sync with fastlane/metadata/android/en-US/changelogs/*.txt (newest first)
const releases: Release[] = [
  {
    version: '1.0.6',
    changes: [
      'Online subtitle search (OpenSubtitles, SubDL, SubSource) built into the player',
      'Equalizer moved to its own quick-access panel',
      'Continue Watching reminders: snooze (1h/2h) or dismiss with one tap',
      'New setting to edit the raw MPV config for advanced users',
      'Numeric percentage overlay for volume/brightness gestures',
      'Private Folder (PIN-protected) now covers downloaded files too',
      'Bug fixes and stability improvements',
    ],
  },
]

export default function Home(): JSX.Element {
  const { siteConfig } = useDocusaurusContext()
  const logoUrl = useBaseUrl('img/logo.png')

  return (
    <Layout title={siteConfig.title} description={siteConfig.tagline}>
      <header className={styles.hero}>
        <img src={logoUrl} alt="Torream" className={styles.logo} />
        <h1 className={styles.heroTitle}>{siteConfig.title}</h1>
        <p className={styles.heroSubtitle}>{siteConfig.tagline}</p>
        <div className={styles.heroButtons}>
          <a href="https://github.com/jonsnow32/torream/releases" className={styles.btnPrimary}>
            Download
          </a>
          <Link to="/docs/getting-started" className={styles.btnSecondary}>
            Documentation
          </Link>
        </div>
      </header>

      <main className={styles.changelog}>
        <h2 className={styles.changelogTitle}>Changelog</h2>
        {releases.map((release) => (
          <section key={release.version} className={styles.release}>
            <h3 className={styles.releaseVersion}>v{release.version}</h3>
            <ul className={styles.releaseList}>
              {release.changes.map((change) => (
                <li key={change}>{change}</li>
              ))}
            </ul>
          </section>
        ))}
      </main>
    </Layout>
  )
}
