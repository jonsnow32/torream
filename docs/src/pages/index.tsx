import useDocusaurusContext from '@docusaurus/useDocusaurusContext'
import Layout from '@theme/Layout'
import Link from '@docusaurus/Link'
import useBaseUrl from '@docusaurus/useBaseUrl'
import styles from './index.module.css'
import { releases } from '@site/src/data/homeChangelog.generated'

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
            <h3 className={styles.releaseVersion}>
              v{release.version} <span className={styles.releaseDate}>{release.date}</span>
            </h3>
            <ul className={styles.releaseList}>
              {release.changes.map((change) => (
                <li key={change}>{change}</li>
              ))}
            </ul>
          </section>
        ))}
        <Link to="/docs/changelog" className={styles.fullChangelogLink}>
          Full changelog →
        </Link>
      </main>
    </Layout>
  )
}
