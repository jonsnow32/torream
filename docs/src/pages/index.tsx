import useDocusaurusContext from '@docusaurus/useDocusaurusContext'
import Layout from '@theme/Layout'

export default function Home(): JSX.Element {
  const {siteConfig} = useDocusaurusContext()
  return (
    <Layout title={siteConfig.title} description={siteConfig.tagline}>
      <main style={{padding: '2rem 0'}}>
        <div style={{textAlign: 'center'}}>
          <h1>{siteConfig.title}</h1>
          <p>{siteConfig.tagline}</p>
        </div>
      </main>
    </Layout>
  )
}
