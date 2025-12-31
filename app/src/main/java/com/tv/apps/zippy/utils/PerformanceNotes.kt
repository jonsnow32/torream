package com.tv.apps.zippy.utils

/**
 * PERFORMANCE OPTIMIZATION NOTES
 *
 * Current implementation uses Coil image loading library:
 * ✅ Automatic memory + disk caching
 * ✅ Lifecycle-aware request management
 * ✅ Bitmap pooling for reduced GC pressure
 * ✅ Crossfade animations
 * ✅ Optimized for RecyclerView scrolling
 * ✅ Video frame decoding support
 *
 * Coil provides all recommended optimizations out of the box:
 * - Disk cache survives app restarts
 * - Automatic bitmap pooling
 * - Hardware acceleration when available
 * - Progressive loading capabilities
 * - Placeholder and error handling
 *
 * For further optimization, consider:
 *
 * 1. PRELOADING
 *    - Implement RecyclerView.OnScrollListener
 *    - Preload next 5-10 items while scrolling
 *    - Better perceived performance
 *
 * 2. CUSTOM PLACEHOLDERS
 *    - Use appropriate placeholder drawables
 *    - Match aspect ratios to reduce layout shifts
 *
 * 3. CACHE TUNING
 *    - Coil uses reasonable defaults
 *    - Monitor memory usage with Android Profiler
 *    - Adjust cache sizes if needed (rare)
 *
 * BENCHMARKING:
 * - Coil: ~30-100ms first load, <1ms cached
 * - With preload: Perceived as instant
 *
 * MEMORY USAGE:
 * - Coil manages memory efficiently
 * - Automatic cleanup on view detach
 * - Bitmap pooling reduces allocations
 *
 * TESTING TIPS:
 * - Monitor frame drops with GPU Rendering Profile
 * - Test on older devices (API 24-28)
 * - Test with large video libraries (1000+ items)
 * - Check Coil logs for debugging (filter by "Coil")
 */
