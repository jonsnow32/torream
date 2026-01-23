import { type Variants, motion } from 'framer-motion'
import Translate from '@docusaurus/Translate'
import Link from '@docusaurus/Link'
import styles from './styles.module.css'
import { Star } from 'lucide-react'

const variants: Variants = {
    visible: i => ({
        opacity: 1,
        y: 0,
        transition: {
            type: 'spring',
            damping: 25,
            stiffness: 100,
            duration: 0.3,
            delay: i * 0.15,
        },
    }),
    hidden: { opacity: 0, y: 30 },
}

export default function Hero() {
    return (
        <motion.div className={styles.hero}>
            {/* Background gradient */}
            <div className={styles.backgroundGradient}></div>

            <div className={styles.container}>
                {/* Left content */}
                <motion.div className={styles.content} initial="hidden" animate="visible">
                    {/* Tag */}
                    <motion.div custom={0} variants={variants} className={styles.tag}>
                        <span className={styles.tagIcon}>▶</span>
                        <Translate id="homepage.hero.tag">STREAMING MEDIA PLAYER</Translate>
                    </motion.div>

                    {/* Headline */}
                    <motion.h1 custom={1} variants={variants} className={styles.headline}>
                        <Translate id="homepage.hero.headline">
                            Stream your library like it was mastered.
                        </Translate>
                    </motion.h1>

                    {/* Description */}
                    <motion.p custom={2} variants={variants} className={styles.description}>
                        <Translate id="homepage.hero.description">
                            ZippyPlayer is a powerful media player with advanced streaming, torrent support, 4K video playback, and deep customization. Experience your content the way it was meant to be enjoyed.
                        </Translate>
                    </motion.p>

                    {/* Features badges */}
                    <motion.div custom={3} variants={variants} className={styles.features}>
                        <div className={styles.featureBadge}>
                            <span className={styles.badgeIcon}>📱</span>
                            <Translate id="homepage.hero.feature1">Optimized for Android</Translate>
                        </div>
                        <div className={styles.featureBadge}>
                            <span className={styles.badgeIcon}>📺</span>
                            <Translate id="homepage.hero.feature2">4K / Streaming Support</Translate>
                        </div>
                        <div className={styles.featureBadge}>
                            <span className={styles.badgeIcon}>🎬</span>
                            <Translate id="homepage.hero.feature3">Torrent & Download</Translate>
                        </div>
                    </motion.div>

                    {/* CTA Buttons */}
                    <motion.div custom={4} variants={variants} className={styles.ctaButtons}>
                        <button className={styles.primaryButton}>
                            <Translate id="homepage.hero.download">Get ZippyPlayer</Translate>
                        </button>
                        <Link
                            href="https://play.google.com/store/apps/details?id=com.zippyplayer"
                            target="_blank"
                            rel="noopener noreferrer"
                            className={styles.secondaryButton}
                        >
                            <span>▶</span>
                            <Translate id="homepage.hero.googleplay">Available on Google Play</Translate>
                        </Link>
                    </motion.div>

                    {/* Rating and testimonial */}
                    <motion.div custom={5} variants={variants} className={styles.testimonial}>
                        <div className={styles.ratingStars}>
                            {[...Array(5)].map((_, i) => (
                                <Star key={i} size={16} fill="currentColor" className={styles.star} />
                            ))}
                        </div>
                        <p className={styles.testimonialText}>
                            <Translate id="homepage.hero.testimonial">
                                Loved by millions of Android users worldwide. Free trial - No subscription required.
                            </Translate>
                        </p>
                    </motion.div>
                </motion.div>

                {/* Right side - Phone mockup */}
                <motion.div
                    className={styles.phoneContainer}
                    initial={{ opacity: 0, x: 50 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ duration: 0.6, delay: 0.3 }}
                >
                    <div className={styles.phoneMockup}>
                        <img
                            src="/img/mobile/phone-mockup.png"
                            alt="ZippyPlayer on mobile"
                            className={styles.phoneImage}
                        />
                    </div>
                </motion.div>
            </div>
        </motion.div>
    )
}
