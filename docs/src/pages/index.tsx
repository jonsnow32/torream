import useDocusaurusContext from '@docusaurus/useDocusaurusContext'
import Layout from '@theme/Layout'
import styles from './index.module.css'
import { useEffect, useCallback, useState } from 'react'
import useEmblaCarousel from 'embla-carousel-react'
import Autoplay from 'embla-carousel-autoplay'

const features = [
  {
    title: 'Powerful Video Player',
    description: 'Built on Media3 (ExoPlayer) supporting all major formats including H.265/HEVC, VP9, AV1, with hardware acceleration and adaptive streaming.',
    image: '/img/tv/tv-scene.png',
    imageAlt: 'Video playback interface'
  },
  {
    title: 'Download Manager',
    description: 'Advanced download manager with HTTP/HTTPS, HLS segmented downloads, resume/pause functionality, and background downloading.',
    image: '/img/tv/tv-scene.png',
    imageAlt: 'Download manager',
    reverse: true
  },
  {
    title: 'Torrent Support',
    description: 'Full-featured torrent client powered by LibTorrent4j. Stream while downloading with magnet links, sequential downloading, and speed controls.',
    image: '/img/tv/tv-scene.png',
    imageAlt: 'Torrent streaming'
  },
  {
    title: 'Media Library',
    description: 'Central hub for organizing video content with automatic cataloging, playlist management, favorites, smart search, and thumbnail generation.',
    image: '/img/tv/tv-scene.png',
    imageAlt: 'Media library',
    reverse: true
  },
  {
    title: 'Chromecast',
    description: 'Built-in Chromecast support with one-tap casting, remote playback control, subtitle support, and audio track selection.',
    image: '/img/tv/tv-scene.png',
    imageAlt: 'Chromecast support'
  },
  {
    title: 'Android TV',
    description: 'Fully optimized for Android TV with leanback UI, D-pad navigation, voice search integration, and 10-foot design for big screens.',
    image: '/img/tv/tv-scene.png',
    imageAlt: 'Android TV interface',
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
                    Download Torream
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
