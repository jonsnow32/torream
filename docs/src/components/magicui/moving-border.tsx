'use client'
import { cn } from '@site/src/lib/utils'
import { motion, useAnimationFrame, useMotionTemplate, useMotionValue, useTransform } from 'framer-motion'
import React, { useRef } from 'react'

export function MovingButton({
  borderRadius = '1.75rem',
  children,
  as: Component = 'div',
  containerClassName,
  borderClassName,
  duration,
  className,
  ...otherProps
}: {
  borderRadius?: string
  children: React.ReactNode
  as?: any
  containerClassName?: string
  borderClassName?: string
  duration?: number
  className?: string
  [key: string]: any
}) {
  return (
    <Component
      className={cn('relative h-14 w-auto min-w-32 overflow-hidden bg-transparent p-[2px] text-xl', containerClassName)}
      style={{
        borderRadius: borderRadius,
      }}
      {...otherProps}
    >
      <div className="absolute inset-0" style={{ borderRadius: `calc(${borderRadius} * 0.96)` }}>
        <MovingBorder duration={duration} rx="30%" ry="30%">
          <div
            className={cn(
              'h-full w-full bg-gradient-to-r from-transparent via-neutral-100 dark:via-neutral-500 to-transparent opacity-0 group-hover/button:opacity-100',
              borderClassName,
            )}
          />
        </MovingBorder>
      </div>

      <div
        className={cn(
          'relative bg-background px-4 py-2 text-primary rounded-[calc(1.75rem-2px)]',
          className,
        )}
        style={{
          borderRadius: `calc(${borderRadius} * 0.96)`,
        }}
      >
        {children}
      </div>
    </Component>
  )
}

export const MovingBorder = ({
  children,
  duration = 5000,
  rx,
  ry,
  ...otherProps
}: {
  children: React.ReactNode
  duration?: number
  rx?: string
  ry?: string
  [key: string]: any
}) => {
  const pathRef = useRef<any>()
  const progress = useMotionValue<number>(0)

  useAnimationFrame((time) => {
    const length = pathRef.current?.getTotalLength()
    if (length) {
      const pxPerMillisecond = length / duration
      progress.set((time * pxPerMillisecond) % length)
    }
  })

  const x = useTransform(progress, val => pathRef.current?.getPointAtLength(val).x)
  const y = useTransform(progress, val => pathRef.current?.getPointAtLength(val).y)

  const transform = useMotionTemplate`translateX(${x}px) translateY(${y}px) translateX(-50%) translateY(-50%)`

  return (
    <>
      <svg
        xmlns="http://www.w3.org/2000/svg"
        preserveAspectRatio="none"
        className="absolute size-full"
        width="100%"
        height="100%"
        {...otherProps}
      >
        <rect fill="none" width="100%" height="100%" rx={rx} ry={ry} ref={pathRef} />
      </svg>
      <motion.div
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          display: 'inline-block',
          width: '20px',
          height: '20px',
          borderRadius: '50%',
          transform,
          background: 'linear-gradient(90deg, transparent, #12affa, transparent)',
          boxShadow: '0 0 20px #12affa',
        }}
      >
        {children}
      </motion.div>
    </>
  )
}
