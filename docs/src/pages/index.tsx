import useDocusaurusContext from '@docusaurus/useDocusaurusContext'
import Layout from '@theme/Layout'
import styles from './index.module.css'
import { useEffect, useCallback, useState } from 'react'
import useEmblaCarousel from 'embla-carousel-react'
import Autoplay from 'embla-carousel-autoplay'

const features = [
  {
    title: 'Powerful Video Playback',
    description: 'Built on mpv, supporting all major video formats with hardware acceleration for smooth playback.',
    image: '/img/tv/tv-scene.png',
    imageAlt: 'Video playback interface'
  },
  {
    title: 'Torrent Streaming',
    description: 'Stream videos directly from torrents without waiting for complete downloads. Advanced peer management for optimal performance.',
    image: '/img/tv/tv-scene.png',
    imageAlt: 'Torrent streaming',
    reverse: true
  },
  {
    title: 'HLS & DASH Support',
    description: 'Seamless adaptive streaming with HLS and DASH protocols. Automatic quality switching based on network conditions.',
    image: '/img/tv/tv-scene.png',
    imageAlt: 'HLS streaming'
  },
  {
    title: 'Modern UI',
    description: 'Beautiful Material Design 3 interface with gesture controls, customizable themes, and intuitive navigation.',
    image: '/img/tv/tv-scene.png',
    imageAlt: 'Modern UI design',
    reverse: true
  }
]

export default function Home(): JSX.Element {
  const {siteConfig} = useDocusaurusContext()
  
  const [emblaRef, emblaApi] = useEmblaCarousel(
    { 
      loop: true,
      align: 'start'
    },
    [Autoplay({ delay: 4000, stopOnInteraction: false })]
  )

  const scrollPrev = useCallback(() => {
    if (emblaApi) emblaApi.scrollPrev()
  }, [emblaApi])

  const scrollNext = useCallback(() => {
    if (emblaApi) emblaApi.scrollNext()
  }, [emblaApi])

  const scrollTo = useCallback((index: number) => {
    if (emblaApi) emblaApi.scrollTo(index)
  }, [emblaApi])

  const [selectedIndex, setSelectedIndex] = useState(0)

  useEffect(() => {
    if (!emblaApi) return

    const onSelect = () => {
      setSelectedIndex(emblaApi.selectedScrollSnap())
    }

    emblaApi.on('select', onSelect)
    onSelect()

    return () => {
      emblaApi.off('select', onSelect)
    }
  }, [emblaApi])

  const totalSlides = features.length + 2

  return (
    <Layout title={siteConfig.title} description={siteConfig.tagline}>
      <div className={styles.sliderContainer}>
        <div className={styles.embla} ref={emblaRef}>
          <div className={styles.emblaContainer}>
            {/* Hero Section */}
            <div className={styles.emblaSlide}>
              <div className={styles.hero}>
                <div className={styles.heroContent}>
                  <h1 className={styles.heroTitle}>{siteConfig.title}</h1>
                  <p className={styles.heroSubtitle}>{siteConfig.tagline}</p>
                  <div className={styles.heroButtons}>
                    <a href="/docs/getting-started" className={styles.btnPrimary}>
                      Get Started
                    </a>
                    <a href="/docs/features/video-player" className={styles.btnSecondary}>
                      Explore Features
                    </a>
                  </div>
                </div>
              </div>
            </div>

            {/* Features Section */}
            {features.map((feature, idx) => (
              <div key={idx} className={styles.emblaSlide}>
                <div className={`${styles.featureRow} ${feature.reverse ? styles.featureRowReverse : ''}`}>
                  <div className={styles.featureText}>
                    <h2>{feature.title}</h2>
                    <p>{feature.description}</p>
                  </div>
                  <div className={styles.featureImageWrapper}>
                    <img 
                      src={feature.image} 
                      alt={feature.imageAlt}
                      className={styles.featureImage}
                    />
                  </div>
                </div>
              </div>
            ))}

            {/* CTA Section */}
            <div className={styles.emblaSlide}>
              <div className={styles.cta}>
                <div className={styles.ctaContent}>
                  <h2>Ready to experience the best Android video player?</h2>
                  <p>Download now and enjoy seamless streaming on your Android device</p>
                  <a href="https://github.com/staronecloud/csplayer/releases" className={styles.btnCta}>
                    Download ZippyPlayer
                  </a>
                </div>
              </div>
            </div>
          </div>

          {/* Navigation Dots */}
          <div className={styles.sliderDots}>
            {Array.from({ length: totalSlides }).map((_, idx) => (
              <button
                key={idx}
                className={`${styles.dot} ${selectedIndex === idx ? styles.activeDot : ''}`}
                onClick={() => scrollTo(idx)}
                aria-label={`Go to slide ${idx + 1}`}
              />
            ))}
          </div>

          {/* Navigation Arrows */}
          <button 
            className={`${styles.arrow} ${styles.arrowLeft}`}
            onClick={scrollPrev}
            aria-label="Previous slide"
          >
            ‹
          </button>
          <button 
            className={`${styles.arrow} ${styles.arrowRight}`}
            onClick={scrollNext}
            aria-label="Next slide"
          >
            ›
          </button>
        </div>
      </div>
    </Layout>
  )
}
